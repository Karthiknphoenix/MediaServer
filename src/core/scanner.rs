use sqlx::SqlitePool;
use walkdir::WalkDir;
use std::path::Path;
use regex::Regex;
use crate::db::models::{Library, LibraryType};
use crate::core::metadata::{fetch_metadata, fetch_season_details};

pub async fn scan_media(pool: &SqlitePool) {
    // Fetch all libraries
    let libraries = sqlx::query_as::<_, Library>("SELECT * FROM libraries")
        .fetch_all(pool)
        .await
        .unwrap_or(vec![]);

    for library in libraries {
        println!("Scanning library: {} (type: {:?})", library.name, library.library_type);
        for entry in WalkDir::new(&library.path).into_iter().filter_map(|e| e.ok()) {
            let path = entry.path();
            if path.is_file() {
                if let Some(ext) = path.extension() {
                    let ext_str = ext.to_string_lossy().to_lowercase();
                    if ["mp4", "mkv", "avi", "mov", "webm", "wmv"].contains(&ext_str.as_str()) {
                        process_video(pool, path, &library).await;
                    }
                }
            }
        }
    }
    
    // Cleanup processing
    cleanup_missing_files(pool).await;
}

async fn cleanup_missing_files(pool: &SqlitePool) {
    println!("Cleaning up missing files...");
    let rows: Vec<(i64, String)> = sqlx::query_as("SELECT id, file_path FROM media")
        .fetch_all(pool)
        .await
        .unwrap_or_default();

    for (id, path_str) in rows {
        let path = Path::new(&path_str);
        if !path.exists() {
            println!("Removing missing file from DB: {}", path_str);
            let _ = sqlx::query("DELETE FROM media WHERE id = ?")
                .bind(id)
                .execute(pool)
                .await;
            
            // Also cleanup progress
            let _ = sqlx::query("DELETE FROM playback_progress WHERE media_id = ?")
                .bind(id)
                .execute(pool)
                .await;
        }
    }
}

/// Parses TV show info from file path
/// Expected structure: {library_path}/{SeriesName}/Season {N}/{episode_file}
/// OR: {library_path}/{SeriesName}/{episode_file} (Assume Season 1)
/// OR: {library_path}/{episode_file} (Use Library Name as Series Name)
fn parse_tv_show_info(path: &Path, library_path: &str, library_name: &str) -> Option<(String, i32, i32)> {
    let relative = path.strip_prefix(library_path).ok()?;
    let components: Vec<_> = relative.components().collect();
    
    if components.is_empty() {
        return None;
    }

    let filename = path.file_stem()?.to_string_lossy().to_string();
    let episode_number = parse_episode_number(&filename).unwrap_or(1); // Default to ep 1 if not parsed

    if components.len() >= 3 {
        // Structure: Series/Season/File
        let series_name = components[0].as_os_str().to_string_lossy().to_string();
        let season_folder = components[1].as_os_str().to_string_lossy().to_lowercase();
        
        let season_number = if let Some(caps) = Regex::new(r"season\s*(\d+)").ok()?.captures(&season_folder) {
            caps.get(1)?.as_str().parse().unwrap_or(1)
        } else {
            1 // Default to season 1 if folder doesn't match "Season X"
        };
        
        return Some((series_name, season_number, episode_number));
    }
    
    if components.len() == 2 {
        // Structure: Series/File (No season folder)
        let series_name = components[0].as_os_str().to_string_lossy().to_string();
        return Some((series_name, 1, episode_number));
    }
    
    if components.len() == 1 {
        // Structure: File is directly in Library root
        // Use Library Name as Series Name
        return Some((library_name.to_string(), 1, episode_number));
    }
    
    None
}

/// Parse episode number from filename using common patterns
fn parse_episode_number(filename: &str) -> Option<i32> {
    let lower = filename.to_lowercase();
    
    // Pattern: S01E05, s1e5
    if let Some(caps) = Regex::new(r"s\d+e(\d+)").ok()?.captures(&lower) {
        return caps.get(1)?.as_str().parse().ok();
    }
    
    // Pattern: 1x05
    if let Some(caps) = Regex::new(r"\d+x(\d+)").ok()?.captures(&lower) {
        return caps.get(1)?.as_str().parse().ok();
    }
    
    // Pattern: Episode 5, Ep 05
    if let Some(caps) = Regex::new(r"ep(?:isode)?\s*(\d+)").ok()?.captures(&lower) {
        return caps.get(1)?.as_str().parse().ok();
    }
    
    // Pattern: - 05 - (common in anime)
    if let Some(caps) = Regex::new(r"[-\s](\d{1,3})[-\s]").ok()?.captures(&lower) {
        return caps.get(1)?.as_str().parse().ok();
    }
    
    // Pattern: E05 at start or after space
    if let Some(caps) = Regex::new(r"(?:^|\s)e(\d+)").ok()?.captures(&lower) {
        return caps.get(1)?.as_str().parse().ok();
    }
    
    None
}

async fn process_video(pool: &SqlitePool, path: &Path, library: &Library) {
    let path_str = path.to_string_lossy().to_string();
    let file_stem = path.file_stem().unwrap().to_string_lossy().to_string();
    
    // Parse TV show info if this is a TV show library
    let (series_name, season_number, episode_number) = 
        if library.library_type == LibraryType::TvShows {
            parse_tv_show_info(path, &library.path, &library.name)
                .map(|(s, sn, en)| (Some(s), Some(sn), Some(en)))
                .unwrap_or((None, None, None))
        } else {
            (None, None, None)
        };
    
    // Check if exists
    let existing_media: Option<(i64, Option<String>, Option<String>, Option<String>, Option<String>, Option<String>, Option<i64>)> = 
        sqlx::query_as("SELECT id, title, year, poster_url, plot, media_type, tmdb_id FROM media WHERE file_path = ?")
        .bind(&path_str)
        .fetch_optional(pool)
        .await
        .unwrap_or(None);

    if existing_media.is_none() {
        // Insert new entry with TV show metadata
        let _ = sqlx::query(
            "INSERT INTO media (file_path, title, library_id, series_name, season_number, episode_number) 
             VALUES (?, ?, ?, ?, ?, ?)"
        )
            .bind(&path_str)
            .bind(&file_stem)
            .bind(library.id)
            .bind(&series_name)
            .bind(season_number)
            .bind(episode_number)
            .execute(pool)
            .await;

        // Fetch metadata for new entries
        let search_term = series_name.as_ref().unwrap_or(&file_stem);
        
        if library.library_type == LibraryType::Other {
            return;
        }

        let media_type_hint = if library.library_type == LibraryType::TvShows {
            Some("series")
        } else {
            Some("movie")
        };


        // For TV shows, try to reuse metadata from existing episodes of the same series
        let mut cached_metadata: Option<(Option<i64>, Option<String>, Option<String>, Option<String>, Option<String>, Option<i64>)> = None;
        if library.library_type == LibraryType::TvShows && series_name.is_some() {
            cached_metadata = sqlx::query_as(
                "SELECT year, poster_url, plot, media_type, backdrop_url, tmdb_id FROM media WHERE series_name = ? AND tmdb_id IS NOT NULL LIMIT 1"
            )
            .bind(series_name.as_ref().unwrap())
            .fetch_optional(pool)
            .await
            .unwrap_or(None);
        }

        if let Some((year, poster, plot, media_type, backdrop, tmdb_id)) = cached_metadata {
             // Reuse existing SERIES metadata, but try to fetch EPISODE metadata if we have a TMDB ID
             let mut final_plot = plot;
             let final_poster = poster;
             let mut final_still: Option<String> = None;
             let mut final_title = None; // Keep filename as title default unless we find episode title

             if let Some(tid) = tmdb_id {
                 // Try to fetch episode specific details
                 if let (Some(sn), Some(en)) = (season_number, episode_number) {
                     // Get API Key
                     let tmdb_key: Option<String> = sqlx::query_scalar("SELECT value FROM settings WHERE key = 'tmdb_api_key'")
                        .fetch_optional(pool)
                        .await
                        .unwrap_or(None);
                        
                     if let Some(key) = tmdb_key {
                         if let Ok(episodes) = fetch_season_details(tid, sn, &key).await {
                             if let Some(ep) = episodes.iter().find(|e| e.episode_number == en) {
                                 final_title = Some(ep.name.clone());
                                 if !ep.overview.is_empty() {
                                     final_plot = Some(ep.overview.clone());
                                 }
                                 if let Some(still) = &ep.still_path {
                                     final_still = Some(format!("https://image.tmdb.org/t/p/w500{}", still));
                                 }
                             }
                         }
                     }
                 }
             }

             let _ = sqlx::query(
                 "UPDATE media SET year = ?, poster_url = ?, plot = ?, media_type = ?, backdrop_url = ?, tmdb_id = ?, title = COALESCE(?, title), still_url = ? WHERE file_path = ?"
             )
                .bind(year)
                .bind(final_poster)
                .bind(final_plot)
                .bind(media_type)
                .bind(backdrop)
                .bind(tmdb_id)
                .bind(final_title)
                .bind(final_still)
                .bind(&path_str)
                .execute(pool)
                .await;
             println!("Reused/Fetched metadata for: {} (Series: {})", file_stem, series_name.as_ref().unwrap());
        } else if let Ok(metadata) = fetch_metadata(search_term, media_type_hint, pool).await {
             if library.library_type == LibraryType::TvShows {
                 // Tv Show: Update metadata
                 let mut final_plot = Some(metadata.plot);
                 let final_poster = Some(metadata.poster);
                 let mut final_still: Option<String> = None;
                 let mut final_title = None;
                 let tmdb_id = metadata.tmdb_id;

                 // Attempt to fetch episode details since this is the first time we found the series
                 if let Some(tid) = tmdb_id {
                     if let (Some(sn), Some(en)) = (season_number, episode_number) {
                         let tmdb_key: Option<String> = sqlx::query_scalar("SELECT value FROM settings WHERE key = 'tmdb_api_key'")
                            .fetch_optional(pool)
                            .await
                            .unwrap_or(None);
                            
                         if let Some(key) = tmdb_key {
                             if let Ok(episodes) = fetch_season_details(tid, sn, &key).await {
                                 if let Some(ep) = episodes.iter().find(|e| e.episode_number == en) {
                                     final_title = Some(ep.name.clone());
                                     if !ep.overview.is_empty() {
                                         final_plot = Some(ep.overview.clone());
                                     }
                                     if let Some(still) = &ep.still_path {
                                         final_still = Some(format!("https://image.tmdb.org/t/p/w500{}", still));
                                     }
                                 }
                             }
                         }
                     }
                 }

                 let genres_str = metadata.genres.map(|g| g.join(", "));
                 let _ = sqlx::query(
                     "UPDATE media SET year = ?, poster_url = ?, plot = ?, media_type = ?, backdrop_url = ?, series_name = ?, tmdb_id = ?, title = COALESCE(?, title), still_url = ?, runtime = ?, genres = ? WHERE file_path = ?"
                 )
                    .bind(metadata.year.parse::<i64>().unwrap_or(0))
                    .bind(final_poster)
                    .bind(final_plot)
                    .bind(metadata.media_type)
                    .bind(metadata.backdrop)
                    .bind(metadata.title) // Ensure series name is correct from API
                    .bind(tmdb_id)
                    .bind(final_title)
                    .bind(final_still)
                    .bind(metadata.runtime)
                    .bind(genres_str)
                    .bind(&path_str)
                    .execute(pool)
                    .await;
             } else {
                 // Movie: Update everything including title
                 let genres_str = metadata.genres.map(|g| g.join(", "));
                 let _ = sqlx::query(
                     "UPDATE media SET title = ?, year = ?, poster_url = ?, plot = ?, media_type = ?, backdrop_url = ?, tmdb_id = ?, runtime = ?, genres = ? WHERE file_path = ?"
                 )
                    .bind(metadata.title)
                    .bind(metadata.year.parse::<i64>().unwrap_or(0))
                    .bind(metadata.poster)
                    .bind(metadata.plot)
                    .bind(metadata.media_type)
                    .bind(metadata.backdrop)
                    .bind(metadata.tmdb_id)
                    .bind(metadata.runtime)
                    .bind(genres_str)
                    .bind(&path_str)
                    .execute(pool)
                    .await;
             }
             println!("Added metadata for: {}", file_stem);
        } else {
            println!("Could not fetch metadata for: {}", file_stem);
        }
    } else {
        // Media exists: Update library_id to ensure it belongs to the current library
        let _ = sqlx::query("UPDATE media SET library_id = ? WHERE file_path = ?")
            .bind(library.id)
            .bind(&path_str)
            .execute(pool)
            .await;

        if library.library_type == LibraryType::TvShows && series_name.is_some() {
        // Update existing TV show entries that might be missing series metadata or need episode refresh
        // For now, let's just make sure series info is there. Full refresh needed for existing items to get episode images.
        let _ = sqlx::query(
            "UPDATE media SET series_name = ?, season_number = ?, episode_number = ? 
             WHERE file_path = ? AND series_name IS NULL"
        )
            .bind(&series_name)
            .bind(season_number)
            .bind(episode_number)
            .bind(&path_str)
            .execute(pool)
            .await;
        // logic for updating existing items specifically for this task could be added here, 
        // but typically we rely on "Refresh Metadata" or re-scanning if not in DB.
        // The user might need to "Refresh Metadata" for existing items.
        // We will leave it as is for existing items to avoid re-fetching everything on every scan.
    }
}
}


