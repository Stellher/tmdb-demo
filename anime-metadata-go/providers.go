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
}
func NewProviders(client *http.Client,token string)*Providers{
    return &Providers{client:client,token:token,cache:make(map[string]cachedPayload)}
}
func (p *Providers) doJSON(ctx context.Context,method,endpoint string,input any,authorization bool,cacheFor time.Duration,result any)error{
    var body io.Reader
    if input!=nil {
        data,e:=json.Marshal(input);if e!=nil{return e}
        body=bytes.NewReader(data)
    }
    key:=method+" "+endpoint
    if cacheFor>0 {
        p.mu.RLock();entry,ok:=p.cache[key];p.mu.RUnlock()
        if ok&&entry.expires.After(time.Now()){return json.Unmarshal(entry.raw,result)}
    }
    req,e:=http.NewRequestWithContext(ctx,method,endpoint,body);if e!=nil{return e}
    req.Header.Set("User-Agent","Sparkle-Anime-Metadata/0.1")
    req.Header.Set("Accept","application/json")
    if input!=nil{req.Header.Set("Content-Type","application/json")}
    if authorization{
        if p.token==""{return errors.New("TMDB_READ_TOKEN not configured")}
        req.Header.Set("Authorization","Bearer "+p.token)
    }
    resp,e:=p.client.Do(req);if e!=nil{return e}
    defer resp.Body.Close()
    raw,e:=io.ReadAll(io.LimitReader(resp.Body,8<<20));if e!=nil{return e}
    if resp.StatusCode<200||resp.StatusCode>=300{
        if len(raw)>180{raw=raw[:180]}
        return fmt.Errorf("upstream HTTP %d: %s",resp.StatusCode,strings.TrimSpace(string(raw)))
    }
    if e=json.Unmarshal(raw,result);e!=nil{return fmt.Errorf("invalid upstream JSON: %w",e)}
    if cacheFor>0{
        p.mu.Lock();p.cache[key]=cachedPayload{raw:raw,expires:time.Now().Add(cacheFor)};p.mu.Unlock()
    }
    return nil
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
    },false,0,&result)
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
    },false,0,&result)
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
