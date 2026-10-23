package main

import (
    "context"
    "encoding/json"
    "errors"
    "fmt"
    "io"
    "log"
    "net/http"
    "sort"
    "strconv"
    "strings"
    "sync"
    "time"
)

// Source mappings are third-party published static files. There is NO database,
// persistent cache or local/manual corrections. The entire index lives in RAM.
const (
    aniBridgeURL = "https://github.com/anibridge/anibridge-mappings/releases/download/v3/mappings.json"
    fribbIndexURL = "https://raw.githubusercontent.com/Fribb/anime-lists/master/indices/anilist_index.json"
    fribbMiniURL = "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-mini.json"
)

type Range struct { Start int `json:"start"`; End int `json:"end"` }
type Segment struct {
    AniListID int `json:"anilistId"`
    TMDBID int `json:"tmdbId"`
    TMDBSeason int `json:"tmdbSeason"`
    TMDBRange Range `json:"tmdbRange"`
    AniListRange Range `json:"anilistRange"`
    Source string `json:"source"`
}
type FribbEntry struct {
    AnimeListID int `json:"anilist_id"`
    TMDB struct { TV int `json:"tv"`; Movie json.RawMessage `json:"movie"` } `json:"themoviedb_id"`
    Season struct { TMDB int `json:"tmdb"` } `json:"season"`
    Offset struct { TMDB int `json:"tmdb"` } `json:"episode_offset"`
}
type mappingSnapshot struct {
    byAnime map[int][]Segment
    byTMDB map[int][]int
    fribb map[int]FribbEntry
    updated time.Time
    aniBridgeCount int
    fribbCount int
}
type MappingStore struct {
    mu sync.RWMutex
    snap *mappingSnapshot
    http *http.Client
    lastError string
}
func NewMappingStore(client *http.Client) *MappingStore {
    return &MappingStore{http:client, snap:&mappingSnapshot{
        byAnime:make(map[int][]Segment),byTMDB:make(map[int][]int),fribb:make(map[int]FribbEntry),
    }}
}
func parseRange(s string) (Range,error) {
    s=strings.TrimSpace(s)
    parts:=strings.SplitN(s,"-",2)
    if len(parts)!=2 { return Range{},fmt.Errorf("invalid episode range %q",s) }
    start,e:=strconv.Atoi(parts[0]);if e!=nil||start<=0 { return Range{},fmt.Errorf("invalid range start %q",s) }
    end:=int(^uint(0)>>1)
    if parts[1]!="" { end,e=strconv.Atoi(parts[1]);if e!=nil||end<start { return Range{},fmt.Errorf("invalid range end %q",s) } }
    return Range{start,end},nil
}
func parseAniBridge(raw []byte) (map[int][]Segment,map[int][]int,error) {
    var dataset map[string]json.RawMessage
    if e:=json.Unmarshal(raw,&dataset);e!=nil { return nil,nil,e }
    byAnime:=make(map[int][]Segment)
    reverseSeen:=make(map[int]map[int]struct{})
    for descriptor,body:=range dataset {
        parts:=strings.Split(descriptor,":")
        if len(parts)!=3||parts[0]!="tmdb_show"||!strings.HasPrefix(parts[2],"s"){continue}
        tmdb,e1:=strconv.Atoi(parts[1]);season,e2:=strconv.Atoi(strings.TrimPrefix(parts[2],"s"))
        if e1!=nil||e2!=nil||tmdb<=0||season<0{continue}
        var targets map[string]json.RawMessage
        if json.Unmarshal(body,&targets)!=nil{continue}
        for target,rangesRaw:=range targets{
            if !strings.HasPrefix(target,"anilist:"){continue}
            aid,e:=strconv.Atoi(strings.TrimPrefix(target,"anilist:"));if e!=nil||aid<=0{continue}
            var ranges map[string]string
            if json.Unmarshal(rangesRaw,&ranges)!=nil{continue}
            for tmdbRangeStr,animeRangeStr:=range ranges{
                tmdbRange,e1:=parseRange(tmdbRangeStr)
                animeRange,e2:=parseRange(animeRangeStr)
                if e1!=nil||e2!=nil{continue}
                byAnime[aid]=append(byAnime[aid],Segment{
                    AniListID:aid,TMDBID:tmdb,TMDBSeason:season,TMDBRange:tmdbRange,AniListRange:animeRange,Source:"anibridge",
                })
                if reverseSeen[tmdb]==nil{reverseSeen[tmdb]=make(map[int]struct{})}
                reverseSeen[tmdb][aid]=struct{}{}
            }
        }
    }
    byTMDB:=make(map[int][]int)
    for tmdb,ids:=range reverseSeen {
        for id:=range ids {byTMDB[tmdb]=append(byTMDB[tmdb],id)}
        sort.Ints(byTMDB[tmdb])
    }
    for id:=range byAnime {
        sort.Slice(byAnime[id],func(i,j int)bool{
            a,b:=byAnime[id][i],byAnime[id][j]
            if a.AniListRange.Start!=b.AniListRange.Start{return a.AniListRange.Start<b.AniListRange.Start}
            if a.TMDBSeason!=b.TMDBSeason{return a.TMDBSeason<b.TMDBSeason}
            return a.TMDBRange.Start<b.TMDBRange.Start
        })
    }
    if len(byAnime)==0{return nil,nil,errors.New("AniBridge yielded zero usable entries")}
    return byAnime,byTMDB,nil
}
func parseFribb(indexData,miniData []byte)(map[int]FribbEntry,error){
    var index map[string]struct{ Positions []int `json:"anime-list"` }
    if e:=json.Unmarshal(indexData,&index);e!=nil{return nil,e}
    var mini []FribbEntry
    if e:=json.Unmarshal(miniData,&mini);e!=nil{return nil,e}
    result:=make(map[int]FribbEntry)
    for id,reference:=range index{
        aid,e:=strconv.Atoi(id);if e!=nil||len(reference.Positions)==0{continue}
        pos:=reference.Positions[0]
        if pos<0||pos>=len(mini){continue}
        record:=mini[pos];record.AnimeListID=aid
        result[aid]=record
    }
    if len(result)==0{return nil,errors.New("Fribb yielded zero entries")}
    return result,nil
}
func (s *MappingStore) download(ctx context.Context,endpoint string,maxBytes int64)([]byte,error){
    request,e:=http.NewRequestWithContext(ctx,http.MethodGet,endpoint,nil);if e!=nil{return nil,e}
    request.Header.Set("User-Agent","Sparkle-Anime-Metadata/0.1 (+https://github.com/Stellher/tmdb-demo)")
    resp,e:=s.http.Do(request);if e!=nil{return nil,e}
    defer resp.Body.Close()
    if resp.StatusCode!=200{return nil,fmt.Errorf("%s HTTP %d",endpoint,resp.StatusCode)}
    body,e:=io.ReadAll(io.LimitReader(resp.Body,maxBytes+1))
    if e!=nil{return nil,e}
    if int64(len(body))>maxBytes{return nil,errors.New("mapping payload exceeds size limit")}
    return body,nil
}
// Refresh fetches outside lock and swaps atomically. Partial failures retain
// the previous working index; no user records or overrides are ever written.
func (s *MappingStore) Refresh(ctx context.Context) error {
    ctx,cancel:=context.WithTimeout(ctx,3*time.Minute);defer cancel()
    var byAnime map[int][]Segment
    var byTMDB map[int][]int
    var fribb map[int]FribbEntry
    raw,e:=s.download(ctx,aniBridgeURL,80<<20)
    if e==nil{byAnime,byTMDB,e=parseAniBridge(raw)}
    abError:=e
    fi,e1:=s.download(ctx,fribbIndexURL,30<<20)
    fm,e2:=s.download(ctx,fribbMiniURL,60<<20)
    var fError error
    if e1!=nil{fError=e1}else if e2!=nil{fError=e2}else{fribb,fError=parseFribb(fi,fm)}
    s.mu.Lock();defer s.mu.Unlock()
    prior:=s.snap
    next:=&mappingSnapshot{byAnime:prior.byAnime,byTMDB:prior.byTMDB,fribb:prior.fribb,updated:time.Now()}
    if abError==nil{next.byAnime=byAnime;next.byTMDB=byTMDB}
    if fError==nil{next.fribb=fribb}
    next.aniBridgeCount=len(next.byAnime);next.fribbCount=len(next.fribb)
    s.snap=next
    if abError!=nil||fError!=nil{
        s.lastError=fmt.Sprintf("AniBridge: %v; Fribb: %v",abError,fError)
        return errors.New(s.lastError)
    }
    s.lastError=""
    return nil
}
func(s *MappingStore) AutoRefresh(ctx context.Context) {
    t:=time.NewTicker(24*time.Hour);defer t.Stop()
    for {select {case <-ctx.Done():return;case <-t.C:
        if e:=s.Refresh(ctx);e!=nil{log.Printf("mapping refresh warning: %v",e)}
    }}
}
func(s *MappingStore) Segments(aid int) []Segment{
    s.mu.RLock();defer s.mu.RUnlock()
    result:=append([]Segment(nil),s.snap.byAnime[aid]...)
    if len(result)>0{return result}
    // Fribb is a fallback; AniBridge exact episode ranges always win.
    item,ok:=s.snap.fribb[aid]
    if !ok||item.TMDB.TV==0{return nil}
    season:=item.Season.TMDB
    if season==0{season=1}
    from:=item.Offset.TMDB
    if from<=0{from=1}
    return []Segment{{AniListID:aid,TMDBID:item.TMDB.TV,TMDBSeason:season,
        TMDBRange:Range{Start:from,End:int(^uint(0)>>1)},
        AniListRange:Range{Start:1,End:int(^uint(0)>>1)},Source:"fribb"}}
}
func(s *MappingStore) AnimeIDs(tmdbID int) []int {
    s.mu.RLock();defer s.mu.RUnlock()
    set:=map[int]bool{}
    for _,id:=range s.snap.byTMDB[tmdbID]{set[id]=true}
    for id,e:=range s.snap.fribb{if e.TMDB.TV==tmdbID{set[id]=true}}
    ids:=make([]int,0,len(set))
    for id:=range set{ids=append(ids,id)}
    sort.Ints(ids)
    return ids
}
func(s *MappingStore) Stats()any{
    s.mu.RLock();defer s.mu.RUnlock()
    return map[string]any{"anibridgeAnimeIds":s.snap.aniBridgeCount,"fribbAnimeIds":s.snap.fribbCount,
        "loaded":len(s.snap.byAnime)>0||len(s.snap.fribb)>0,
        "updatedAt":s.snap.updated,"lastRefreshWarning":s.lastError,
        "storage":"in-memory only; no DB/no overrides"}
}
