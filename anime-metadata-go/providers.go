package main

import (
    "bytes"
    "context"
    "encoding/json"
    "errors"
    "fmt"
    "io"
    "net/http"
    "net/url"
    "strconv"
    "strings"
    "sync"
    "time"
)

type ImageInfo struct {
    Large string `json:"large"`
    ExtraLarge string `json:"extraLarge"`
}
type TitleInfo struct {
    English string `json:"english"`
    Romaji string `json:"romaji"`
    Native string `json:"native"`
}
type DateInfo struct { Year int `json:"year"`; Month int `json:"month"`; Day int `json:"day"` }
type RelationEdge struct {
    RelationType string `json:"relationType"`
    Node struct {
        ID int `json:"id"`
        Format string `json:"format"`
        Episodes *int `json:"episodes"`
        Title TitleInfo `json:"title"`
    } `json:"node"`
}
type AniMedia struct {
    ID int `json:"id"`
    IDMal int `json:"idMal"`
    Title TitleInfo `json:"title"`
    Format string `json:"format"`
    Status string `json:"status"`
    Episodes *int `json:"episodes"`
    StartDate DateInfo `json:"startDate"`
    CoverImage ImageInfo `json:"coverImage"`
    BannerImage string `json:"bannerImage"`
    Relations struct{ Edges []RelationEdge `json:"edges"` } `json:"relations"`
}
func (m AniMedia) Name() string {
    if m.Title.English!="" {return m.Title.English}
    if m.Title.Romaji!="" {return m.Title.Romaji}
    return m.Title.Native
}
type TmdbEpisode struct {
    ID int `json:"id"`
    EpisodeNumber int `json:"episode_number"`
    SeasonNumber int `json:"season_number"`
    Name string `json:"name"`
    Overview string `json:"overview"`
    AirDate string `json:"air_date"`
    StillPath string `json:"still_path"`
    Runtime int `json:"runtime"`
}
type TmdbSeason struct {
    ID int `json:"id"`
    Name string `json:"name"`
    Episodes []TmdbEpisode `json:"episodes"`
}
type cachedPayload struct { raw []byte; expires time.Time }
type Providers struct {
    client *http.Client
    token string
    mu sync.RWMutex
    cache map[string]cachedPayload
    aniListGate chan struct{}
}
func NewProviders(client *http.Client,token string)*Providers{
    return &Providers{client:client,token:token,cache:make(map[string]cachedPayload),aniListGate:make(chan struct{},2)}
}
func (p *Providers) doJSON(ctx context.Context,method,endpoint string,input any,authorization bool,cacheFor time.Duration,result any)error{
    var payload []byte
    if input!=nil{
        data,e:=json.Marshal(input);if e!=nil{return e}
        payload=data
    }
    key:=method+" "+endpoint
    // GraphQL shares a POST endpoint but different queries/IDs. They must not
    // share a cache entry merely because the URL is the same.
    if len(payload)>0{key+=" "+string(payload)}
    lookup:=func()bool{
        if cacheFor<=0{return false}
        p.mu.RLock()
        entry,ok:=p.cache[key]
        p.mu.RUnlock()
        if !ok||!entry.expires.After(time.Now()){return false}
        return json.Unmarshal(entry.raw,result)==nil
    }
    if lookup(){return nil}
    if endpoint==anilistGraphQL{
        select{case p.aniListGate<-struct{}{}:
            defer func(){<-p.aniListGate}()
        case <-ctx.Done():return ctx.Err()}
        // Another request may have filled the same cached ID while we waited.
        if lookup(){return nil}
    }
    if authorization&&p.token==""{return errors.New("TMDB_READ_TOKEN not configured")}
    var last error
    for attempt:=0;attempt<3;attempt++ {
        var body io.Reader
        if len(payload)>0 {body=bytes.NewReader(payload)}
        req,e:=http.NewRequestWithContext(ctx,method,endpoint,body);if e!=nil{return e}
        req.Header.Set("User-Agent","Sparkle-Anime-Metadata/0.1")
        req.Header.Set("Accept","application/json")
        if input!=nil{req.Header.Set("Content-Type","application/json")}
        if authorization{req.Header.Set("Authorization","Bearer "+p.token)}
        resp,e:=p.client.Do(req)
        var raw []byte
        var retryAfter time.Duration
        if e==nil{
            raw,e=io.ReadAll(io.LimitReader(resp.Body,8<<20))
            resp.Body.Close()
            if resp.StatusCode==429{
                if seconds,nerr:=strconv.Atoi(resp.Header.Get("Retry-After"));nerr==nil&&seconds>0&&seconds<=5{
                    retryAfter=time.Duration(seconds)*time.Second
                }
            }
        }
        retry:=false
        if e!=nil{
            last=e
            retry=true
        }else if resp.StatusCode<200||resp.StatusCode>=300{
            if len(raw)>180{raw=raw[:180]}
            last=fmt.Errorf("upstream HTTP %d: %s",resp.StatusCode,strings.TrimSpace(string(raw)))
            retry=resp.StatusCode==429||resp.StatusCode>=500
        }else{
            if e=json.Unmarshal(raw,result);e!=nil{return fmt.Errorf("invalid upstream JSON: %w",e)}
            // Do not cache rate-limited GraphQL 200 bodies carrying "errors".
            if endpoint==anilistGraphQL{
                var envelope struct{Errors []json.RawMessage `json:"errors"`}
                if json.Unmarshal(raw,&envelope)==nil&&len(envelope.Errors)>0{
                    return fmt.Errorf("AniList GraphQL errors: %s",string(envelope.Errors[0]))
                }
            }
            if cacheFor>0{
                p.mu.Lock()
                if len(p.cache)>4000{
                    for k,v:=range p.cache{
                        if time.Now().After(v.expires){delete(p.cache,k)}
                    }
                    if len(p.cache)>4000 {
                        for k:=range p.cache{delete(p.cache,k);if len(p.cache)<=3500{break}}
                    }
                }
                p.cache[key]=cachedPayload{raw:raw,expires:time.Now().Add(cacheFor)}
                p.mu.Unlock()
            }
            return nil
        }
        if !retry||attempt==2 {break}
        delay:=time.Duration(300*(1<<attempt))*time.Millisecond
        if retryAfter>delay{delay=retryAfter}
        timer:=time.NewTimer(delay)
        select{case <-ctx.Done():timer.Stop();return ctx.Err();case <-timer.C:}
    }
    return last
}
const anilistGraphQL = "https://graphql.anilist.co"
const mediaQuery = `query ($id: Int) {
 Media(id: $id, type: ANIME) {
  id idMal title { english romaji native } format status episodes startDate { year month day }
  coverImage { large extraLarge } bannerImage
  relations { edges { relationType node { id format episodes title { english romaji native } } } }
 }
}`
func(p *Providers) Anime(ctx context.Context,id int)(AniMedia,error){
    var result struct { Data struct { Media AniMedia `json:"Media"` } `json:"data"`; Errors []any `json:"errors"` }
    err:=p.doJSON(ctx,"POST",anilistGraphQL, map[string]any{
        "query":mediaQuery,"variables":map[string]int{"id":id},
    },false,6*time.Hour,&result)
    if err!=nil{return AniMedia{},err}
    if result.Data.Media.ID<=0{return AniMedia{},fmt.Errorf("AniList title %d not found / GraphQL errors %v",id,result.Errors)}
    return result.Data.Media,nil
}
func (p *Providers) Search(ctx context.Context,query string)([]AniMedia,error){
    const gql=`query($search:String) {
 Page(page:1,perPage:25){media(search:$search,type:ANIME,sort:SEARCH_MATCH){
  id idMal title { english romaji native } format status episodes startDate { year month day }
  coverImage { large extraLarge } bannerImage
 }}
}`
    var result struct { Data struct { Page struct { Media []AniMedia `json:"media"` } `json:"Page"` } `json:"data"`; Errors []any `json:"errors"` }
    err:=p.doJSON(ctx,"POST",anilistGraphQL,map[string]any{
        "query":gql,"variables":map[string]string{"search":query},
    },false,5*time.Minute,&result)
    if err!=nil{return nil,err}
    if result.Data.Page.Media==nil&&len(result.Errors)>0{return nil,fmt.Errorf("AniList search errors: %v",result.Errors)}
    return result.Data.Page.Media,nil
}
func (p *Providers) Season(ctx context.Context,id,season int)(TmdbSeason,error) {
    var x TmdbSeason
    endpoint:="https://api.themoviedb.org/3/tv/"+strconv.Itoa(id)+"/season/"+
        strconv.Itoa(season)+"?language=zh-CN"
    err:=p.doJSON(ctx,http.MethodGet,endpoint,nil,true,12*time.Hour,&x)
    if err!=nil{return TmdbSeason{},err}
    return x,nil
}
func (p *Providers) FindTMDB(ctx context.Context,query string)(int,error){
    endpoint:="https://api.themoviedb.org/3/search/tv?query="+url.QueryEscape(query)+
        "&language=zh-CN&include_adult=false"
    var res struct { Results []struct {
        ID int `json:"id"`
        Name string `json:"name"`
        OriginalName string `json:"original_name"`
        GenreIDs []int `json:"genre_ids"`
    } `json:"results"` }
    if e:=p.doJSON(ctx,"GET",endpoint,nil,true,1*time.Hour,&res);e!=nil{return 0,e}
    normalize:=func(s string)string{return strings.ToLower(strings.Join(strings.Fields(s),""))}
    n:=normalize(query)
    for _,v:=range res.Results {
        if normalize(v.Name)!=n&&normalize(v.OriginalName)!=n{continue}
        for _,genre:=range v.GenreIDs{if genre==16{return v.ID,nil}}
    }
    return 0,fmt.Errorf("no trustworthy exact animation title match for %q; refusing to guess",query)
}
