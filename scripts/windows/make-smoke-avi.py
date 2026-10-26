"""Generate a tiny, deterministic uncompressed video; no external media/credentials."""
import struct
import sys
from pathlib import Path

def chunk(kind, data):
    return kind + struct.pack('<I', len(data)) + data + b'\0' * (len(data) % 2)

w, h, frames, fps = 64, 36, 20, 10
frame_size = w * h * 3
avih = struct.pack('<14I', 1000000 // fps, frame_size * fps, 0, 0x10,
                   frames, 0, 1, frame_size, w, h, 0, 0, 0, 0)
strh = struct.pack('<4s4sIHH8I4h', b'vids', b'DIB ', 0, 0, 0,
                   0, 1, fps, 0, frames, frame_size, 0xffffffff, 0, 0, 0, w, h)
strf = struct.pack('<IiiHHIIiiII', 40, w, h, 1, 24, 0, frame_size, 0, 0, 0, 0)
hdrl = chunk(b'LIST', b'hdrl' + chunk(b'avih', avih) +
             chunk(b'LIST', b'strl' + chunk(b'strh', strh) + chunk(b'strf', strf)))
movi, index = bytearray(), bytearray()
for frame in range(frames):
    pixels = bytes(v for y in range(h) for x in range(w)
                   for v in ((x * 4 + frame * 7) % 256, y * 7 % 256, frame * 12 % 256))
    index += struct.pack('<4sIII', b'00db', 0x10, len(movi) + 4, len(pixels))
    movi += chunk(b'00db', pixels)
data = b'AVI ' + hdrl + chunk(b'LIST', b'movi' + movi) + chunk(b'idx1', index)
Path(sys.argv[1]).write_bytes(chunk(b'RIFF', data))
