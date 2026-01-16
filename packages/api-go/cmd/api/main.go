package main

import (
	"fmt"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/coulterpeterson/floatnative/packages/api-go/internal/database"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/router"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/services"
)

func main() {
	// Initialize Database
	if err := database.Connect(); err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}
	defer database.Close()

	// Run Migrations (Assuming ./migrations is mounted or copied in Docker)
	// In Docker, we copied migrations folder to root /app/migrations
	if err := database.RunMigrations("migrations"); err != nil {
		log.Printf("Warning: Failed to run migrations: %v", err)
		// We initiate log but don't fail fatal yet to allow debugging if needed, 
		// but typically this should be fatal.
	}

	r := router.New()

	// Start Background Workers
	go func() {
		// Initial run with delay to ensure DB is ready? DB connect is blocking so it's fine.
		log.Println("Starting LTT Posts Updater...")
		if err := services.UpdateLTTPosts(); err != nil {
			log.Printf("Failed initial LTT update: %v", err)
		}
		
		ticker := time.NewTicker(1 * time.Hour)
		for range ticker.C {
			if err := services.UpdateLTTPosts(); err != nil {
				log.Printf("Failed scheduled LTT update: %v", err)
			}
		}
	}()

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	log.Printf("Server starting on port %s", port)
	if err := http.ListenAndServe(":"+port, r); err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}
}
