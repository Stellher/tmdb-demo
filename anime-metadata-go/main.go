package main

import(
    "context"
    "log"
    "net/http"
    "os"
    "strconv"
    "strings"
    "time"
    "github.com/gin-gonic/gin"
)

func newRouter(s *Service)*gin.Engine {
    r:=gin.New()
    r.Use(gin.Recovery())
    r.GET("/health",func(c *gin.Context){
        c.JSON(http.StatusOK,gin.H{"status":"ok","mappings":s.maps.Stats(),
            "tmdbConfigured":s.api.token!=""})
    })
    r.GET("/api/anime/search",func(c *gin.Context){
        q:=strings.TrimSpace(c.Query("q"))
        if q==""{c.JSON(400,gin.H{"error":"q is required"});return}
        ctx,cancel:=context.WithTimeout(c.Request.Context(),18*time.Second);defer cancel()
        matches,e:=s.api.Search(ctx,q)
        if e!=nil{c.JSON(502,gin.H{"error":e.Error()});return}
        c.JSON(200,gin.H{"query":q,"results":matches,"count":len(matches)})
    })
    r.GET("/api/anime/tmdb/:id",func(c *gin.Context){
        id,e:=strconv.Atoi(c.Param("id"));if e!=nil||id<=0{c.JSON(400,gin.H{"error":"invalid TMDB ID"});return}
        ctx,cancel:=context.WithTimeout(c.Request.Context(),90*time.Second);defer cancel()
        result,e:=s.Series(ctx,id)
        if e!=nil{c.JSON(502,gin.H{"error":e.Error(),"tmdbId":id});return}
        c.JSON(200,result)
    })
    r.GET("/api/anime/:id",func(c *gin.Context){
        id,e:=strconv.Atoi(c.Param("id"));if e!=nil||id<=0{c.JSON(400,gin.H{"error":"invalid AniList ID"});return}
        ctx,cancel:=context.WithTimeout(c.Request.Context(),30*time.Second);defer cancel()
        r,e:=s.One(ctx,id,c.Query("include_upcoming")=="true")
        if e!=nil{c.JSON(502,gin.H{"error":e.Error(),"anilistId":id});return}
        c.JSON(200,r)
    })
    return r
}
func main(){
    client:=&http.Client{Timeout:65*time.Second}
    store:=NewMappingStore(client)
    if e:=store.Refresh(context.Background());e!=nil{
        log.Printf("mapping startup warning (API will retry daily): %v",e)
    }
    token:=os.Getenv("TMDB_READ_TOKEN")
    service:=NewService(store,NewProviders(client,token))
    ctx,cancel:=context.WithCancel(context.Background());defer cancel()
    go store.AutoRefresh(ctx)
    port:=os.Getenv("PORT");if port==""{port="8080"}
    log.Printf("anime-metadata-go listening on :%s; DB=none",port)
    if e:=newRouter(service).Run(":"+port);e!=nil{log.Fatal(e)}
}
