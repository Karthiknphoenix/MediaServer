use sqlx::SqlitePool;
use walkdir::WalkDir;
use std::path::Path;
use crate::db::models::{Media, Library}; // Import Library
use crate::core::metadata::fetch_metadata;

pub async fn scan_media(pool: &SqlitePool) { // Removed media_path arg
    // Fetch all libraries
    let libraries = sqlx::query_as::<_, Library>("SELECT * FROM libraries")
        .fetch_all(pool)
        .await
        .unwrap_or(vec![]);

    for library in libraries {
        println!("Scanning library: {}", library.name);
        for entry in WalkDir::new(&library.path).into_iter().filter_map(|e| e.ok()) {
            let path = entry.path();
            if path.is_file() {
                if let Some(ext) = path.extension() {
                    let ext_str = ext.to_string_lossy().to_lowercase();
                    // Basic check, can be refined based on library type later
                    if ["mp4", "mkv", "avi", "mov"].contains(&ext_str.as_str()) {
                        process_video(pool, path, &library).await;
                    }
                }
            }
        }
    }
}

async fn process_video(pool: &SqlitePool, path: &Path, library: &Library) {
    let path_str = path.to_string_lossy().to_string();
    
    // Check if exists
    let exists: bool = sqlx::query_scalar("SELECT EXISTS(SELECT 1 FROM media WHERE file_path = ?)")
        .bind(&path_str)
        .fetch_one(pool)
        .await
        .unwrap_or(false);

    if !exists {
        let file_stem = path.file_stem().unwrap().to_string_lossy().to_string();
        
        // Initial insert with library_id
        // We use a transaction or just simple insert. 
        // Note: We need to handle the case where library_id is required now.
        let _ = sqlx::query("INSERT INTO media (file_path, title, library_id) VALUES (?, ?, ?)")
            .bind(&path_str)
            .bind(&file_stem)
            .bind(library.id)
            .execute(pool)
            .await;

        // Fetch metadata
        // Fetch metadata
        // TODO: Use LibraryType to decide fetching strategy
        if let Ok(metadata) = fetch_metadata(&file_stem, pool).await {
             let _ = sqlx::query("UPDATE media SET title = ?, year = ?, poster_url = ?, plot = ?, media_type = ? WHERE file_path = ?")
                .bind(metadata.title)
                .bind(metadata.year.parse::<i64>().unwrap_or(0))
                .bind(metadata.poster)
                .bind(metadata.plot)
                .bind(metadata.media_type)
                .bind(&path_str)
                .execute(pool)
                .await;
             println!("Added metadata for: {}", file_stem);
        } else {
            println!("Could not fetch metadata for: {}", file_stem);
        }
    }
}
