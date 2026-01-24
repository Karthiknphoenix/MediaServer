mod db;
mod api;
mod core;

use std::env;
use std::net::SocketAddr;
use dotenv::dotenv;
use crate::db::init_db;
use crate::api::routes::app;
use crate::core::scanner::scan_media;
use tower_http::services::ServeDir;

#[tokio::main]
async fn main() {
    dotenv().ok();
    
    // Logging w/ strict type requirement fix if needed, but for now basic:
    tracing_subscriber::fmt::init();

    let pool = init_db().await;
    
    // Background scan
    let pool_clone = pool.clone();
    // Removed hardcoded media_path env reading, now uses libraries from DB
    
    tokio::spawn(async move {
        scan_media(&pool_clone).await;
    });

    // Router with static file serving
    let app = app(pool)
        .nest_service("/", ServeDir::new("static"));

    let addr = SocketAddr::from(([127, 0, 0, 1], 3000));
    println!("listening on {}", addr);
    
    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}
