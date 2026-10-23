package main

import (
    "encoding/json"
    "testing"
)
func TestMultiRangeEpisodeMapping(t *testing.T) {
    raw:=[]byte(`{
     "$meta":{"version":3},
     "tmdb_show:65942:s1":{"anilist:108632":{"26-31":"1-6","32-38":"7-13"},
        "anilist:119661":{"39-50":"1-12"}},
     "tmdb_show:65931:s2":{"anilist:999":{"1-12":"1-12"}}
    }`)
    indexed,reverse,e:=parseAniBridge(raw)
    if e!=nil{t.Fatal(e)}
    if len(indexed[108632])!=2{t.Fatalf("must preserve both ranges: %+v",indexed[108632])}
    if len(reverse[65942])!=2{t.Fatalf("missing ReZero related parts: %v",reverse[65942])}
    t1:=TmdbSeason{Episodes:[]TmdbEpisode{}}
    for n:=1;n<=85;n++{t1.Episodes=append(t1.Episodes,TmdbEpisode{EpisodeNumber:n,StillPath:"/image.jpg"})}
    eps,warnings:=applyRanges(indexed[108632],map[int]TmdbSeason{1:t1},nil,true)
    if len(warnings)>0{t.Fatal(warnings)}
    if len(eps)!=13||eps[0].Number!=1||eps[0].TMDBEpisodeNumber!=26||eps[12].TMDBEpisodeNumber!=38 {
        t.Fatalf("wrong split cour mapping: first=%+v last=%+v length=%d",eps[0],eps[len(eps)-1],len(eps))
    }
}
func TestRejectMismatchedRanges(t *testing.T){
    seg:=Segment{AniListID:1,TMDBID:5,TMDBSeason:1,TMDBRange:Range{Start:1,End:12},AniListRange:Range{Start:1,End:13}}
    eps,warn:=applyRanges([]Segment{seg},map[int]TmdbSeason{1:{Episodes:[]TmdbEpisode{{EpisodeNumber:1}}}},nil,true)
    if len(eps)!=0||len(warn)==0{t.Fatal("invalid range must not invent episode correspondence")}
}
func TestFribbIndexes(t *testing.T){
    index:=[]byte(`{"108632":{"anime-list":[1]}}`)
    mini:=[]byte(`[{"themoviedb_id":{"tv":111}},{"themoviedb_id":{"tv":65942},"season":{"tmdb":1},"episode_offset":{"tmdb":26}}]`)
    entries,e:=parseFribb(index,mini)
    if e!=nil{t.Fatal(e)}
    b,_:=json.Marshal(entries[108632])
    if entries[108632].TMDB.TV!=65942||entries[108632].Offset.TMDB!=26 {t.Fatal(string(b))}
}

func TestRejectTripleConflict(t *testing.T) {
    segments:=[]Segment{
        {AniListID:1,TMDBID:3,TMDBSeason:1,TMDBRange:Range{1,1},AniListRange:Range{1,1}},
        {AniListID:1,TMDBID:3,TMDBSeason:2,TMDBRange:Range{1,1},AniListRange:Range{1,1}},
        {AniListID:1,TMDBID:3,TMDBSeason:3,TMDBRange:Range{1,1},AniListRange:Range{1,1}},
    }
    seasons:=map[int]TmdbSeason{}
    for i:=1;i<=3;i++ {seasons[i]=TmdbSeason{Episodes:[]TmdbEpisode{{EpisodeNumber:1,StillPath:"/x.jpg"}}}}
    result,warnings:=applyRanges(segments,seasons,nil,true)
    if len(result)!=0||len(warnings)==0{t.Fatalf("should exclude 3-way ambiguous correspondence; got %v %v",result,warnings)}
}
