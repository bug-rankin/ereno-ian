UC10 Delayed Replay Variants
============================

- Original delayed replay: selected trip (cbStatus=1) messages are delayed by a sampled amount and re-inserted.
- Batch dump delayed replay: selected messages are delayed, then released back-to-back with micro-gaps after the last delayed message's window.
- Backoff delayed replay: selected messages are delayed, then flushed at a faster-than-normal interval (configurable multiplier); legitimate messages that arrive during the flush are queued and sent in the accelerated stream.
- Double drop delayed replay: selected messages are delayed and then replace the earliest available slots after the delay window, dropping the originals in those positions.
