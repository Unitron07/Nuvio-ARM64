"""Regenerate synthetic JPEGs (Pillow needed only for regeneration, not owner tests)."""
from pathlib import Path
import random
from PIL import Image, ImageDraw

root = Path(__file__).parent / 'fixtures' / 'stage3' / 'artwork'
root.mkdir(exist_ok=True)
for index in range(24):
    width, height = [(480, 720), (1000, 1500), (1536, 2304)][index // 8]
    random.seed(index)
    small = Image.new('RGB', (240, 360))
    pixels = small.load()
    for y in range(360):
        for x in range(240):
            pixels[x, y] = ((x + index * 17 + random.randrange(32)) % 256,
                            (y + index * 31 + random.randrange(32)) % 256,
                            (x + y + index * 13) % 256)
    image = small.resize((width, height), Image.Resampling.BICUBIC)
    draw = ImageDraw.Draw(image)
    for k in range(12):
        draw.rectangle((width // 12 + k * width // 30, height // 8 + k * height // 30,
                        width // 2 + k * width // 35, height // 3 + k * height // 35),
                       outline=(220, 180, 90), width=max(1, width // 240))
    draw.rectangle((width // 12, height * 3 // 4, width * 11 // 12, height * 11 // 12), fill=(20, 25, 40))
    draw.text((width // 8, height * 4 // 5), 'SYNTHETIC POSTER ' + str(index), fill='white')
    image.save(root / (str(index) + '.jpg'), quality=94)
