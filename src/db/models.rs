use sqlx::FromRow;
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, Clone, sqlx::Type, PartialEq)]
#[sqlx(type_name = "TEXT", rename_all = "snake_case")]
pub enum LibraryType {
    Movies,
    TvShows,
    MusicVideos,
}

#[derive(Debug, FromRow, Serialize, Deserialize, Clone)]
pub struct Setting {
    pub key: String,
    pub value: String,
}

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
}
