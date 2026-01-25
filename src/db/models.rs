use sqlx::FromRow;
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, Clone, sqlx::Type, PartialEq)]
#[sqlx(type_name = "TEXT", rename_all = "snake_case")]
#[serde(rename_all = "snake_case")]
pub enum LibraryType {
    Movies,
    TvShows,
    MusicVideos,
    Other,
}

#[derive(Debug, FromRow, Serialize, Deserialize, Clone)]
pub struct Setting {
    pub key: String,
    pub value: String,
}

#[allow(dead_code)]
#[derive(Debug, FromRow, Serialize, Deserialize, Clone)]
pub struct PlaybackProgress {
    pub media_id: i64,
    pub position: i64, // seconds
    pub total_duration: i64, // seconds
    pub last_watched: chrono::NaiveDateTime,
}

#[derive(Debug, FromRow, Serialize, Deserialize, Clone)]
pub struct Library {
    pub id: i64,
    pub name: String,
    pub path: String,
    pub library_type: LibraryType,
}

#[derive(Debug, FromRow, Serialize, Deserialize, Clone)]
pub struct Media {
    pub id: i64,
    pub library_id: i64,
    pub file_path: String,
    pub title: Option<String>,
    pub year: Option<i64>,
    pub poster_url: Option<String>,
    pub plot: Option<String>,
    pub media_type: Option<String>, // "movie" or "episode", kept for compatibility/metadata
    pub added_at: Option<chrono::NaiveDateTime>,
    // TV Show specific fields
    pub series_name: Option<String>,
    pub season_number: Option<i32>,
    pub episode_number: Option<i32>,
    pub tmdb_id: Option<i64>,
    pub backdrop_url: Option<String>,
    pub still_url: Option<String>,
    pub runtime: Option<i32>,
    pub genres: Option<String>,
    pub library_type: Option<LibraryType>,
}
