<?php

declare(strict_types=1);

namespace FloatNative\Api;

/** A request body Go's json.Decoder would have refused for the handler's struct. */
final class BadBody extends \Exception
{
}
