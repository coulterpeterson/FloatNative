-- Migration: Drop system_config table
-- Reason: Moving floatplane_sails_sid to Worker Secret for better security
-- Date: 2025-11-22

DROP TABLE IF EXISTS `system_config`;
