package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func main() {
	// 1. Generate Mock Access Token
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"sub": "testuser_dpop",
		"exp": time.Now().Add(time.Hour).Unix(),
	})
	accessToken, _ := token.SignedString([]byte("secret"))

	// 2. Generate Mock DPoP Proof
	// Needs a JWK in header
	// Create a dummy key structure directly since we don't sign with it for this Mock
	// Our extractor expects map with crv/kty/x/y or just serializes what it finds.
	// Let's use ES256 style fields as per extraction logic
	jwk := map[string]interface{}{
		"kty": "EC",
		"crv": "P-256",
		"x":   "mockX",
		"y":   "mockY",
	}

	proofToken := jwt.New(jwt.SigningMethodHS256)
	proofToken.Header["typ"] = "dpop+jwt"
	proofToken.Header["jwk"] = jwk
	proofToken.Claims = jwt.MapClaims{
		"jti": "mockJTI",
		"htm": "POST",
		"htu": "http://localhost:8080/auth/login",
		"iat": time.Now().Unix(),
	}
	dpopProof, _ := proofToken.SignedString([]byte("secret"))

	// 3. Perform Request
	body := map[string]string{
		"access_token": accessToken,
		"dpop_proof":   dpopProof,
		"device_info":  "Mock Device",
	}
	jsonBody, _ := json.Marshal(body)

	req, _ := http.NewRequest("POST", "http://api:8080/auth/login", strings.NewReader(string(jsonBody)))
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		return
	}
	defer resp.Body.Close()

	respBytes, _ := io.ReadAll(resp.Body)
	fmt.Printf("Status: %s\n", resp.Status)
	fmt.Printf("Response: %s\n", string(respBytes))
}
