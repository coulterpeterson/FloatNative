package router

import (
	"fmt"
	"net/http"

	"github.com/coulterpeterson/floatnative/packages/api-go/internal/database"
	"github.com/coulterpeterson/floatnative/packages/api-go/internal/handlers"
	appMiddleware "github.com/coulterpeterson/floatnative/packages/api-go/internal/middleware"
	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

func New() *chi.Mux {
	router := chi.NewRouter()

	// Middleware
	router.Use(middleware.Logger)
	router.Use(middleware.Recoverer)

	// Routes
	router.Get("/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"name":"Floatplane Companion API","status":"running"}`))
	})

	router.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if err := database.Pool.Ping(r.Context()); err != nil {
			w.WriteHeader(http.StatusServiceUnavailable)
			w.Write([]byte(fmt.Sprintf(`{"status":"error","db":"%v"}`, err)))
			return
		}
		w.Write([]byte(`{"status":"ok"}`))
	})

	// Authenticated Routes
	router.Group(func(r chi.Router) {
		r.Use(appMiddleware.AuthMiddleware)
		r.Post("/auth/logout", handlers.Logout)
		r.Get("/playlists", handlers.GetPlaylists)
		r.Post("/playlists", handlers.CreatePlaylist)
		r.Put("/playlists/{id}", handlers.UpdatePlaylist)
		r.Delete("/playlists/{id}", handlers.DeletePlaylist)
		r.Patch("/playlists/{id}/add", handlers.AddVideoToPlaylist)
		r.Patch("/playlists/{id}/remove", handlers.RemoveVideoFromPlaylist)

		// Watch Later Routes
		r.Get("/watch-later", handlers.GetWatchLater)
		r.Put("/watch-later", handlers.UpdateWatchLater)
		r.Patch("/watch-later/add", handlers.AddVideoToWatchLater)
		r.Patch("/watch-later/remove", handlers.RemoveVideoFromWatchLater)

		// LTT Routes
		r.Get("/ltt/search", handlers.SearchLTT)

	})

	// Auth QR Routes (Public)
	router.Post("/auth/qr/generate", handlers.GenerateQR)
	router.Get("/auth/qr/poll/{id}", handlers.PollQR)
	router.Post("/auth/login", handlers.Login)

	return router
}
