package main

import (
    "context"
    "fmt"
    "sort"
    "strings"
    "sync"
    "time"
)

type Episode struct {
    Number int `json:"number"`
    Season int `json:"tmdbSeason"`
    TMDBEpisodeNumber int `json:"tmdbEpisodeNumber"`
    TMDBEpisodeID int `json:"tmdbEpisodeId"`
    Title string `json:"title"`
    Overview string `json:"overview"`
    AirDate string `json:"airDate"`
    Image string `json:"image,omitempty"`
    Runtime int `json:"runtimeMinutes,omitempty"`
}
type AnimeResult struct {
    AniListID int `json:"anilistId"`
    TMDBID int `json:"tmdbId,omitempty"`
    MALID int `json:"malId,omitempty"`
    Title string `json:"title"`
    TitleNative string `json:"titleNative,omitempty"`
    Format string `json:"format"`
    StartDate DateInfo `json:"startDate"`
    DeclaredEpisodes *int `json:"anilistEpisodeCount"`
    Poster string `json:"poster,omitempty"`
    MappingSource string `json:"mappingSource"`
    Episodes []Episode `json:"episodes"`
    Count int `json:"count"`
    Stills int `json:"stills"`
    VerifiedCount bool `json:"verifiedCount"`
    Warnings []string `json:"warnings"`
}
type SeriesResult struct {
    TMDBID int `json:"tmdbId"`
    Entries []AnimeResult `json:"entries"`
    MainSeasonAniListIDs []int `json:"mainSeasonAniListIds"`
    ExtraAniListIDs []int `json:"extraAniListIds"`
    TotalEpisodes int `json:"totalEpisodes"`
    TotalStills int `json:"totalStills"`
    Unresolved []int `json:"unresolvedAniListIds,omitempty"`
    Notes []string `json:"notes"`
}
type Service struct{
    maps *MappingStore
    api *Providers
}
func NewService(store *MappingStore,api *Providers)*Service{return &Service{store,api}}
func img(path string)string {
    if path=="" {return ""}
    if strings.HasPrefix(path,"http://")||strings.HasPrefix(path,"https://"){return path}
    return "https://image.tmdb.org/t/p/w500"+path
}
// applyRanges handles ALL ranges, including non-contiguous and cross-season
// mappings. It preserves the original TMDB season/episode for auditability.
func applyRanges(mapping []Segment,seasonData map[int]TmdbSeason,
    declared *int,includeUpcoming bool)([]Episode,[]string) {
    episodes:=make([]Episode,0)
    warnings:=make([]string,0)
    seen:=make(map[string]struct{})
    now:=time.Now().UTC().Format("2006-01-02")
    for _,seg:=range mapping {
        raw,ok:=seasonData[seg.TMDBSeason]
        if !ok {warnings=append(warnings,fmt.Sprintf("TMDB season %d not found",seg.TMDBSeason));continue}
        tmdbLen:=seg.TMDBRange.End-seg.TMDBRange.Start
        animeLen:=seg.AniListRange.End-seg.AniListRange.Start
        if seg.TMDBRange.End!=int(^uint(0)>>1)&&seg.AniListRange.End!=int(^uint(0)>>1)&&tmdbLen!=animeLen {
            warnings=append(warnings,fmt.Sprintf("unequal episode ranges S%d: TMDB %d-%d -> AniList %d-%d; not inventing episode links",
                seg.TMDBSeason,seg.TMDBRange.Start,seg.TMDBRange.End,seg.AniListRange.Start,seg.AniListRange.End))
            continue
        }
        matched:=0
        for _,v:=range raw.Episodes {
            n:=v.EpisodeNumber
            if n<seg.TMDBRange.Start||n>seg.TMDBRange.End {continue}
            animeNum:=seg.AniListRange.Start+(n-seg.TMDBRange.Start)
            if animeNum<1||animeNum>seg.AniListRange.End {continue}
            if declared!=nil&&*declared>0&&animeNum>*declared {continue}
            if !includeUpcoming&&v.AirDate!=""&&v.AirDate>now{continue}
            key:=fmt.Sprintf("%d:%d:%d",animeNum,seg.TMDBSeason,n)
            if _,exists:=seen[key];exists{continue}
            seen[key]=struct{}{}
            episodes=append(episodes,Episode{
                Number:animeNum,Season:seg.TMDBSeason,TMDBEpisodeNumber:n,TMDBEpisodeID:v.ID,
                Title:v.Name,Overview:v.Overview,AirDate:v.AirDate,Image:img(v.StillPath),Runtime:v.Runtime,
            })
            matched++
        }
        if matched==0{warnings=append(warnings,fmt.Sprintf("no mapped aired episodes for TMDB season %d range %d-%d",
            seg.TMDBSeason,seg.TMDBRange.Start,seg.TMDBRange.End))}
    }
    sort.Slice(episodes,func(i,j int)bool{
        if episodes[i].Number!=episodes[j].Number{return episodes[i].Number<episodes[j].Number}
        if episodes[i].Season!=episodes[j].Season{return episodes[i].Season<episodes[j].Season}
        return episodes[i].TMDBEpisodeNumber<episodes[j].TMDBEpisodeNumber
    })
    // Two different provider items referencing the same AniList number is a
    // mapping conflict: do not silently choose the wrong episode/still.
    filtered:=make([]Episode,0,len(episodes))
    for _,v:=range episodes {
        if len(filtered)>0&&filtered[len(filtered)-1].Number==v.Number {
            warnings=append(warnings,fmt.Sprintf("conflicting mapping for AniList episode %d; excluded ambiguous artwork",v.Number))
            filtered=filtered[:len(filtered)-1]
            continue
        }
        filtered=append(filtered,v)
    }
    return filtered,warnings
}
func(s *Service) One(ctx context.Context,aid int,includeUpcoming bool)(AnimeResult,error){
    media,e:=s.api.Anime(ctx,aid)
    if e!=nil{return AnimeResult{},e}
    segs:=s.maps.Segments(aid)
    if len(segs)==0 {
        // Conservative last resort: exact-name match for an unsplit series.
        // Never guess a season from a loose title similarity.
        id,err:=s.api.FindTMDB(ctx,media.Name())
        if err!=nil{return AnimeResult{},fmt.Errorf("no AniBridge/Fribb mapping for AniList %d: %w",aid,err)}
        segs=[]Segment{{AniListID:aid,TMDBID:id,TMDBSeason:1,
            TMDBRange:Range{1,int(^uint(0)>>1)},AniListRange:Range{1,int(^uint(0)>>1)},Source:"exact-title-fallback"}}
    }
    var warnings []string
    tmdbID:=segs[0].TMDBID
    seasons:=make(map[int]TmdbSeason)
    unique:=make(map[int]struct{})
    // Fribb-only offsets sometimes overlap the final episode of the previous
    // mapped cour. Shift only when BOTH neighboring AniBridge boundaries prove
    // an unambiguous correction; do not keep a human-maintained fix table.
    if len(segs)==1 && segs[0].Source=="fribb" &&
        media.Format=="TV" && media.Episodes!=nil && *media.Episodes>0 {
        from:=segs[0].TMDBRange.Start
        anchors:=s.maps.Anchors(tmdbID,segs[0].TMDBSeason)
        leftAligned,rightAligned:=false,false
        for _,anchor:=range anchors {
            if anchor.TMDBRange.End==from {leftAligned=true}
            if anchor.TMDBRange.Start==from+*media.Episodes+1 {rightAligned=true}
        }
        if leftAligned && rightAligned {
            segs[0].TMDBRange.Start=from+1
            segs[0].TMDBRange.End=from+*media.Episodes
            segs[0].AniListRange.End=*media.Episodes
            segs[0].Source="fribb-validated-boundaries"
        }
    }
    // Fribb fallback's season=1 for OVA/SPECIAL/MOVIE is often false.
    // Resolve only a UNIQUE matching S0 air date for a single-episode
    // special. A multi-episode OVA cannot safely be inferred from dates.
    if len(segs)==1 && segs[0].Source=="fribb" &&
        (media.Format=="OVA" || media.Format=="SPECIAL" || media.Format=="MOVIE") {
        if media.Episodes==nil || *media.Episodes!=1 {
            return AnimeResult{},fmt.Errorf("AniList %d special has no episode-exact mapping; refusing to reuse TMDB regular S1",aid)
        }
        special,e:=s.api.Season(ctx,tmdbID,0)
        if e!=nil {return AnimeResult{},fmt.Errorf("TMDB specials unavailable: %w",e)}
        date:=fmt.Sprintf("%04d-%02d-%02d",media.StartDate.Year,media.StartDate.Month,media.StartDate.Day)
        var matches []TmdbEpisode
        for _,ep:=range special.Episodes {
            if ep.AirDate==date {matches=append(matches,ep)}
        }
        if len(matches)!=1 {
            return AnimeResult{},fmt.Errorf("AniList %d special S0 date %s maps to %d episodes, not one; refusing a guess",aid,date,len(matches))
        }
        segs[0]=Segment{AniListID:aid,TMDBID:tmdbID,TMDBSeason:0,
            TMDBRange:Range{Start:matches[0].EpisodeNumber,End:matches[0].EpisodeNumber},
            AniListRange:Range{Start:1,End:1},Source:"special-exact-date"}
    }
    for _,seg:=range segs {
        if seg.TMDBID!=tmdbID {
            warnings=append(warnings,"mapping spans multiple TMDB show IDs; only consistent show used")
            continue
        }
        unique[seg.TMDBSeason]=struct{}{}
    }
    var wg sync.WaitGroup
    var mu sync.Mutex
    for n:=range unique {
        wg.Add(1)
        go func(season int) {
            defer wg.Done()
            result,err:=s.api.Season(ctx,tmdbID,season)
            mu.Lock();defer mu.Unlock()
            if err!=nil {
                warnings=append(warnings,fmt.Sprintf("TMDB S%d: %v",season,err))
            }else{seasons[season]=result}
        }(n)
    }
    wg.Wait()
    eps,issues:=applyRanges(segs,seasons,media.Episodes,includeUpcoming)
    warnings=append(warnings,issues...)
    if len(eps)==0 {return AnimeResult{},fmt.Errorf("no episodes resolved for AniList %d / TMDB %d: %v",aid,tmdbID,warnings)}
    cover:=media.CoverImage.ExtraLarge
    if cover==""{cover=media.CoverImage.Large}
    result:=AnimeResult{AniListID:aid,TMDBID:tmdbID,MALID:media.IDMal,
        Title:media.Name(),TitleNative:media.Title.Native,Format:media.Format,
        StartDate:media.StartDate,DeclaredEpisodes:media.Episodes,
        Poster:cover,MappingSource:segs[0].Source,Episodes:eps,
        Count:len(eps),Warnings:warnings}
    for _,ep:=range eps{if ep.Image!=""{result.Stills++}}
    result.VerifiedCount=media.Episodes!=nil&&len(eps)==*media.Episodes
    if !result.VerifiedCount{
        warnings=append(warnings,"returned count differs from AniList declared total (may be airing/incomplete)")
        result.Warnings=warnings
    }
    return result,nil
}
// Series collects every AniList title mapped to this TMDB series. Each AniList
// entry is a distinct TV cour/part, preserving specials instead of flattening.
func(s *Service) Series(ctx context.Context,tmdbID int)(SeriesResult,error){
    ids:=s.maps.AnimeIDs(tmdbID)
    if len(ids)==0{return SeriesResult{},fmt.Errorf("TMDB %d not found in loaded AniBridge/Fribb mapping",tmdbID)}
    if len(ids)>45 {ids=ids[:45]}
    response:=SeriesResult{TMDBID:tmdbID,Entries:[]AnimeResult{},Notes:[]string{
        "Entries follow AniList anime titles/cours, not raw TMDB season numbers.",
        "Source mappings are maintained by upstream communities; no manual override database.",
    }}
    type item struct{id int;result AnimeResult;err error}
    results:=make(chan item,len(ids))
    semaphore:=make(chan struct{},4)
    var wg sync.WaitGroup
    for _,id:=range ids{
        wg.Add(1)
        go func(aid int){
            defer wg.Done()
            select{case semaphore<-struct{}{}:case <-ctx.Done():results<-item{id:aid,err:ctx.Err()};return}
            defer func(){<-semaphore}()
            r,e:=s.One(ctx,aid,false)
            results<-item{id:aid,result:r,err:e}
        }(id)
    }
    wg.Wait();close(results)
    for r:=range results{
        if r.err!=nil {response.Unresolved=append(response.Unresolved,r.id);continue}
        response.Entries=append(response.Entries,r.result)
        if r.result.Format=="TV" {
            response.MainSeasonAniListIDs=append(response.MainSeasonAniListIDs,r.id)
        } else {
            response.ExtraAniListIDs=append(response.ExtraAniListIDs,r.id)
        }
        response.TotalEpisodes+=r.result.Count
        response.TotalStills+=r.result.Stills
    }
    sort.Ints(response.Unresolved)
    sort.Ints(response.MainSeasonAniListIDs)
    sort.Ints(response.ExtraAniListIDs)
    sort.Slice(response.Entries,func(i,j int)bool{
        a,b:=response.Entries[i].StartDate,response.Entries[j].StartDate
        if a.Year!=b.Year{return a.Year<b.Year}
        if a.Month!=b.Month{return a.Month<b.Month}
        if a.Day!=b.Day{return a.Day<b.Day}
        return response.Entries[i].AniListID<response.Entries[j].AniListID
    })
    if len(response.Entries)==0{return SeriesResult{},fmt.Errorf("no verified anime entries for TMDB %d; unresolved %v",tmdbID,response.Unresolved)}
    return response,nil
}
