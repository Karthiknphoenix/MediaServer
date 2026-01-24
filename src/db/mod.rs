pub mod models;

use sqlx::sqlite::{SqlitePool, SqlitePoolOptions};
use std::env;

pub async fn init_db() -> SqlitePool {
    let database_url = env::var("DATABASE_URL").expect("DATABASE_URL must be set");
    
    let pool = SqlitePoolOptions::new()
        .max_connections(5)
        .connect(&database_url)
        .await
        .expect("Failed to connect to database");

    // Create Libraries table
    sqlx::query(
        "CREATE TABLE IF NOT EXISTS libraries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            path TEXT NOT NULL,
            library_type TEXT NOT NULL
        );"
    )
    .execute(&pool)
    .await
    .expect("Failed to create libraries table");

    // Create Settings table
    sqlx::query(
        "CREATE TABLE IF NOT EXISTS settings (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );"
    )
    .execute(&pool)
    .await
    .expect("Failed to create settings table");

    // Create Playback Progress table
    sqlx::query(
        "CREATE TABLE IF NOT EXISTS playback_progress (
            media_id INTEGER PRIMARY KEY,
            position INTEGER NOT NULL,
            total_duration INTEGER NOT NULL,
            last_watched DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY(media_id) REFERENCES media(id)
        );"
    )
    .execute(&pool)
    .await
    .expect("Failed to create playback_progress table");

    // Create Media table (initial creation)
    sqlx::query(
        "CREATE TABLE IF NOT EXISTS media (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            file_path TEXT NOT NULL UNIQUE,
            title TEXT,
            year INTEGER,
            poster_url TEXT,
            plot TEXT,
            media_type TEXT,
            added_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );"
    )
    .execute(&pool)
    .await
    .expect("Failed to initialize database");

    // Migration to add library_id if it doesn't exist
    // This is a naive migration check. In production, use sqlx-cli or a migration table.
    let has_library_id: Option<i64> = sqlx::query_scalar(
        "SELECT 1 FROM pragma_table_info('media') WHERE name = 'library_id'"
    )
    .fetch_optional(&pool)
    .await
    .unwrap_or(None);

    if has_library_id.is_none() {
        println!("Migrating database: Adding library_id to media table");
        // We need a default library or allow null. For now, let's create a default library if none exists
        // and assign it. Or simply add the column with default 0/1 if we assume one exists.
        // Better: Add nullable first or default 0. 
        // Let's Add it as INTEGER DEFAULT 0 for now.
        let _ = sqlx::query("ALTER TABLE media ADD COLUMN library_id INTEGER DEFAULT 0").execute(&pool).await;
    }

    pool
}
