<?php

declare(strict_types=1);

namespace FloatNative\Api;

// Port of api-go/internal/services/ltt.go. The Go service ran this on boot
// and hourly in a goroutine; here bin/update-ltt-posts.php runs it from cron.
//
// Unlike Go's strict struct decoding, fields are read leniently, so a type
// change elsewhere in Floatplane's post payload (as happened with
// creator.owner) doesn't fail the whole batch.
final class LttUpdater
{
    public const LTT_CREATOR_ID = '59f94c0bdd241b70349eb72b';

    /** @return int number of posts upserted */
    public static function run(): int
    {
        $apiUrl = Env::get('FLOATPLANE_API_URL', 'https://www.floatplane.com/api');
        $sailsSid = Env::get('FLOATPLANE_SAILS_SID');
        if ($sailsSid === '') {
            throw new \RuntimeException('FLOATPLANE_SAILS_SID is not set');
        }

        $posts = self::fetchCreatorPosts($apiUrl, $sailsSid, self::LTT_CREATOR_ID, 20, 0);

        $stmt = Database::pdo()->prepare(
            'INSERT INTO fp_posts (
                id, title, creator_id, creator_name, channel_id, channel_title, channel_icon_url, thumbnail_url,
                has_video, video_count, video_duration, has_audio, audio_count, audio_duration,
                has_picture, picture_count, is_featured, has_gallery, gallery_count, release_date, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                title = VALUES(title),
                thumbnail_url = VALUES(thumbnail_url),
                video_count = VALUES(video_count),
                updated_at = VALUES(updated_at)'
        );

        foreach ($posts as $post) {
            $creator = self::obj($post['creator'] ?? null);
            $channel = self::obj($post['channel'] ?? null);
            $meta = self::obj($post['metadata'] ?? null);
            try {
                $stmt->execute([
                    self::str($post['id'] ?? null),
                    self::str($post['title'] ?? null),
                    self::str($creator['id'] ?? null),
                    self::str($creator['title'] ?? null),
                    self::str($channel['id'] ?? null),
                    self::str($channel['title'] ?? null),
                    self::bestImagePath($channel['icon'] ?? null),
                    self::bestImagePath($post['thumbnail'] ?? null),
                    (int) !empty($meta['hasVideo']),
                    (int) ($meta['videoCount'] ?? 0),
                    (int) ($meta['videoDuration'] ?? 0),
                    (int) !empty($meta['hasAudio']),
                    (int) ($meta['audioCount'] ?? 0),
                    (int) ($meta['audioDuration'] ?? 0),
                    (int) !empty($meta['hasPicture']),
                    (int) ($meta['pictureCount'] ?? 0),
                    (int) !empty($meta['isFeatured']),
                    (int) !empty($meta['hasGallery']),
                    (int) ($meta['galleryCount'] ?? 0),
                    self::releaseDate($post['releaseDate'] ?? null),
                    Database::now(),
                ]);
            } catch (\PDOException $e) {
                error_log(sprintf('Error upserting LTT post %s: %s', self::str($post['id'] ?? null), $e->getMessage()));
            }
        }

        return count($posts);
    }

    private static function fetchCreatorPosts(string $baseUrl, string $sid, string $creatorId, int $limit, int $offset): array
    {
        $url = $baseUrl . '/content/creator?' . http_build_query([
            'fetchAfter' => $offset,
            'id' => $creatorId,
            'limit' => $limit,
        ]);

        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 10,
            CURLOPT_HTTPHEADER => ['Cookie: sails.sid=' . $sid],
        ]);
        $body = curl_exec($ch);
        $status = curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
        $error = curl_error($ch);

        if ($body === false) {
            throw new \RuntimeException('API request failed: ' . $error);
        }
        if ($status !== 200) {
            throw new \RuntimeException('API request failed with status: ' . $status);
        }

        $posts = json_decode($body, true);
        if (!is_array($posts) || !array_is_list($posts)) {
            throw new \RuntimeException('Unexpected response from /content/creator');
        }
        return array_filter($posts, 'is_array');
    }

    /** Path of the widest child image, else the image's own path. */
    private static function bestImagePath(mixed $image): string
    {
        $image = self::obj($image);
        if ($image === []) {
            return '';
        }
        $children = array_values(array_filter((array) ($image['childImages'] ?? []), 'is_array'));
        if ($children !== []) {
            $best = $children[0];
            foreach ($children as $child) {
                if (($child['width'] ?? 0) > ($best['width'] ?? 0)) {
                    $best = $child;
                }
            }
            return self::str($best['path'] ?? null);
        }
        return self::str($image['path'] ?? null);
    }

    /** RFC 3339 → UTC DATETIME(6); Go stored the zero time when parsing failed. */
    private static function releaseDate(mixed $value): string
    {
        if (is_string($value)) {
            $value = preg_replace('/(\.\d{6})\d+/', '$1', $value); // PHP parses at most microseconds
            $date = \DateTimeImmutable::createFromFormat('Y-m-d\\TH:i:s.uP', $value)
                ?: \DateTimeImmutable::createFromFormat(DATE_RFC3339, $value);
            if ($date !== false) {
                return $date->setTimezone(new \DateTimeZone('UTC'))->format('Y-m-d H:i:s.u');
            }
        }
        return '0001-01-01 00:00:00';
    }

    private static function obj(mixed $value): array
    {
        return is_array($value) ? $value : [];
    }

    private static function str(mixed $value): string
    {
        return is_string($value) ? $value : '';
    }
}
