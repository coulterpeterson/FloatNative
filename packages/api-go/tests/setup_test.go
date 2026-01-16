package tests

import (
	"context"
	"fmt"
	"log"
	"os"
	"testing"

	"github.com/coulterpeterson/floatnative/packages/api-go/internal/database"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/router"
	"github.com/go-chi/chi/v5"
)

func TestMain(m *testing.M) {
	// 1. Setup Database Connection
	// Check for TEST_DB_HOST to execute inside Docker or manually
	if os.Getenv("DB_HOST") == "" {
		// Default to localhost if running locally outside docker but against docker db
		// Assuming ports are mapped 5432:5432
		os.Setenv("DB_HOST", "localhost")
		os.Setenv("DB_PORT", "5432")
		os.Setenv("DB_USER", "postgres")
		os.Setenv("DB_PASSWORD", "postgres")
		os.Setenv("DB_NAME", "floatnative")
	}

	// Connect
	// We use the same Connect function but we might need to be careful about pool usage
	// Ideally we'd modify Connect to accept a config but env vars work for now.
	if err := database.Connect(); err != nil {
		log.Printf("Could not connect to database for tests: %v", err)
		os.Exit(1)
	}

	// 2. Run Migrations
	// We need to resolve the path to migrations. 
	// Since tests are in /tests, migrations are in ../migrations
	if err := database.RunMigrations("../migrations"); err != nil {
		log.Printf("Could not run migrations: %v", err)
		os.Exit(1)
	}

	// 3. Run Tests
	code := m.Run()

	// 4. Teardown
	database.Close()
	os.Exit(code)
}

// Helper to get router
func setupRouter() *chi.Mux {
	return router.New()
}

	// Helper to clear database state between tests
func clearDatabase(t *testing.T) {
	// Truncate relevant tables
	// Tables: device_sessions, playlists, users, qr_sessions
	// fp_posts is reference data, maybe keep or truncate.
	queries := []string{
		"TRUNCATE TABLE device_sessions CASCADE",
		"TRUNCATE TABLE playlists CASCADE",
		"TRUNCATE TABLE qr_sessions CASCADE",
		"TRUNCATE TABLE users CASCADE",
	}

	for _, q := range queries {
		_, err := database.Pool.Exec(context.Background(), q)
		if err != nil {
			t.Logf("Failed to truncate table with query %s: %v", q, err)
		}
	}
}

// Helper to Create a User and get API Key for authenticated requests
func createTestUser(t *testing.T) string {
	userID := "test_user_" + fmt.Sprintf("%d", os.Getpid()) // pseudo random
	apiKey := "test_api_key_" + userID

	// Create User
	// Note: users table has no updated_at column in migration
	// api_key is also in users table as NOT NULL UNIQUE per migration 000001
	// Wait, users table has api_key? Yes. 000001_initial_schema.up.sql:4
	// But device_sessions ALSO has api_key. This seems redundant or specific design.
	// Let's populate providing a dummy api_key for users table, but the effective one for sessions is likely in device_sessions.
	// Actually, looking at migration: users(floatplane_user_id, api_key, created_at, last_accessed_at)
	_, err := database.Pool.Exec(context.Background(), 
		`INSERT INTO users (floatplane_user_id, api_key, created_at, last_accessed_at) 
		 VALUES ($1, $2, NOW(), NOW()) 
		 ON CONFLICT (floatplane_user_id) DO NOTHING`, userID, "user_master_key_"+userID)
	if err != nil {
		t.Fatalf("Failed to create test user: %v", err)
	}

	// Create Device Session (API Key)
	// device_sessions(id, floatplane_user_id, api_key, dpop_jkt, device_info, ...)
	// floatplane_user_id references users(floatplane_user_id)
	sessionID := "sess_" + userID
	_, err = database.Pool.Exec(context.Background(),
		`INSERT INTO device_sessions (id, api_key, floatplane_user_id, device_info, dpop_jkt, last_accessed_at, created_at)
		 VALUES ($1, $2, $3, 'Test Device', $4, NOW(), NOW())`,
		 sessionID, apiKey, userID, "test_jkt_"+userID)
	if err != nil {
		t.Fatalf("Failed to create device session: %v", err)
	}

	return apiKey
}
