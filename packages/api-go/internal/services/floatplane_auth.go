package services

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// ValidateFloatplaneTokenLocally validates a Floatplane OAuth access token locally
// by decoding the JWT and checking expiration. It does NOT verify signature because
// we don't have the public key (or it's DPoP bound).
// Returns the Floatplane User ID (sub).
func ValidateFloatplaneTokenLocally(accessToken string) (string, error) {
	// Parse without verification
	token, _, err := new(jwt.Parser).ParseUnverified(accessToken, jwt.MapClaims{})
	if err != nil {
		return "", fmt.Errorf("failed to parse JWT: %w", err)
	}

	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return "", fmt.Errorf("invalid claims format")
	}

	// Check expiration
	if exp, ok := claims["exp"].(float64); ok {
		if time.Now().Unix() > int64(exp) {
			return "", fmt.Errorf("token expired")
		}
	} else {
		return "", fmt.Errorf("token missing exp claim")
	}

	// Get Subject (User ID)
	sub, ok := claims["sub"].(string)
	if !ok || sub == "" {
		return "", fmt.Errorf("token missing sub claim")
	}

	return sub, nil
}

// ExtractDPoPJKT extracts the JKT (JSON Web Key Thumbprint) from a DPoP proof.
func ExtractDPoPJKT(dpopProof string) (string, error) {
	// 1. Parse JWT to get Header
	// We can't use standard jwt parser easily for header-only extraction if we want raw access,
	// but jwt-go places header in token.Header.
	token, _, err := new(jwt.Parser).ParseUnverified(dpopProof, jwt.MapClaims{})
	if err != nil {
		return "", fmt.Errorf("failed to parse DPoP proof: %w", err)
	}

	// 2. Extract JWK from Header
	jwkRaw, ok := token.Header["jwk"]
	if !ok {
		return "", fmt.Errorf("DPoP proof missing jwk in header")
	}
	
	jwkMap, ok := jwkRaw.(map[string]interface{})
	if !ok {
		// It might be map[string]interface{} already?
		// jwt-go unmarshals JSON numbers as float64, strings as string, objects as map[string]interface{}.
		return "", fmt.Errorf("invalid jwk format")
	}

	// 3. Create Canonical JWK string (sorted keys)
	// We only care about crv, kty, x, y for EC keys which Floatplane likely uses.
	// The TS code: { crv, kty, x, y }
	
	// Create a map with only the required fields
	canonicalJwk := make(map[string]interface{})
	fields := []string{"crv", "kty", "x", "y"}
	for _, f := range fields {
		if val, exists := jwkMap[f]; exists {
			canonicalJwk[f] = val
		} else {
			// If missing a required field for EC, it might be RSA? 
			// But for now let's assume implementation matches TS which specifically explicitly constructs this object.
			// Ideally we should check kty.
		}
	}
	
	// We need to ensure deterministic JSON serialization.
	// Go's encoding/json sorts map keys by default.
	jwkBytes, err := json.Marshal(canonicalJwk) 
	if err != nil {
		return "", fmt.Errorf("failed to marshal canonical JWK: %w", err)
	}

	// 4. SHA-256 Hash
	hash := sha256.Sum256(jwkBytes)

	// 5. Base64 URL Encode
	jkt := base64.RawURLEncoding.EncodeToString(hash[:])

	return jkt, nil
}
