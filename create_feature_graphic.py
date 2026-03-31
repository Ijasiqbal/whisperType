from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math

# Canvas dimensions (exact Play Store requirement)
WIDTH, HEIGHT = 1024, 500

# Brand colors
BG_COLOR_1 = (196, 87, 49)   # Lighter terracotta
BG_COLOR_2 = (168, 68, 35)   # Mid terracotta  
BG_COLOR_3 = (138, 54, 27)   # Darker terracotta

# Create gradient background
canvas = Image.new('RGBA', (WIDTH, HEIGHT), BG_COLOR_1)
draw = ImageDraw.Draw(canvas)

# Diagonal gradient
for x in range(WIDTH):
    for y in range(HEIGHT):
        t = (x / WIDTH * 0.6 + y / HEIGHT * 0.4)
        t = min(1.0, max(0.0, t))
        r = int(BG_COLOR_1[0] + (BG_COLOR_3[0] - BG_COLOR_1[0]) * t)
        g = int(BG_COLOR_1[1] + (BG_COLOR_3[1] - BG_COLOR_1[1]) * t)
        b = int(BG_COLOR_1[2] + (BG_COLOR_3[2] - BG_COLOR_1[2]) * t)
        canvas.putpixel((x, y), (r, g, b, 255))

draw = ImageDraw.Draw(canvas)

# Add subtle wave decoration at the bottom
wave_layer = Image.new('RGBA', (WIDTH, HEIGHT), (0, 0, 0, 0))
wave_draw = ImageDraw.Draw(wave_layer)
for x in range(WIDTH):
    wave_y = int(HEIGHT - 60 + 25 * math.sin(x / 120.0) + 10 * math.sin(x / 50.0))
    for y in range(wave_y, HEIGHT):
        wave_layer.putpixel((x, y), (255, 255, 255, 12))

# Second wave
for x in range(WIDTH):
    wave_y = int(40 + 15 * math.sin(x / 100.0 + 1.5) + 8 * math.sin(x / 40.0))
    for y in range(0, wave_y):
        cur = wave_layer.getpixel((x, y))
        wave_layer.putpixel((x, y), (255, 255, 255, max(cur[3], 8)))

canvas = Image.alpha_composite(canvas, wave_layer)
draw = ImageDraw.Draw(canvas)

# Add subtle radial glow behind logo area
glow_layer = Image.new('RGBA', (WIDTH, HEIGHT), (0, 0, 0, 0))
glow_cx, glow_cy = 740, 250
glow_radius = 200
for x in range(max(0, glow_cx - glow_radius), min(WIDTH, glow_cx + glow_radius)):
    for y in range(max(0, glow_cy - glow_radius), min(HEIGHT, glow_cy + glow_radius)):
        dist = math.sqrt((x - glow_cx)**2 + (y - glow_cy)**2)
        if dist < glow_radius:
            alpha = int(18 * (1 - dist / glow_radius) ** 2)
            glow_layer.putpixel((x, y), (255, 255, 255, alpha))

canvas = Image.alpha_composite(canvas, glow_layer)
draw = ImageDraw.Draw(canvas)

# Load and place the EXACT logo
logo = Image.open('/Users/ijas/Documents/whisperType/ic_launcher_playstore_final.png').convert('RGBA')
logo_size = 260
logo = logo.resize((logo_size, logo_size), Image.LANCZOS)

# Create rounded rectangle mask for the logo
mask = Image.new('L', (logo_size, logo_size), 0)
mask_draw = ImageDraw.Draw(mask)
corner_radius = 52
mask_draw.rounded_rectangle([0, 0, logo_size - 1, logo_size - 1], radius=corner_radius, fill=255)

# Add shadow behind logo
shadow_offset = 6
shadow_layer = Image.new('RGBA', (WIDTH, HEIGHT), (0, 0, 0, 0))
shadow_img = Image.new('RGBA', (logo_size, logo_size), (0, 0, 0, 50))
shadow_img.putalpha(mask)
logo_x = 680
logo_y = (HEIGHT - logo_size) // 2
shadow_layer.paste(shadow_img, (logo_x + shadow_offset, logo_y + shadow_offset), shadow_img)
shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(radius=12))
canvas = Image.alpha_composite(canvas, shadow_layer)

# Paste logo with rounded corners
logo_rounded = Image.new('RGBA', (logo_size, logo_size), (0, 0, 0, 0))
logo_rounded.paste(logo, (0, 0), mask)
canvas.paste(logo_rounded, (logo_x, logo_y), logo_rounded)

draw = ImageDraw.Draw(canvas)

# Typography
bold_font_path = '/System/Library/Fonts/Supplemental/Arial Bold.ttf'
regular_font_path = '/System/Library/Fonts/Supplemental/Arial Narrow Bold.ttf'

try:
    title_font = ImageFont.truetype(bold_font_path, 76)
    tagline_font = ImageFont.truetype(regular_font_path, 24)
except:
    title_font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 76)
    tagline_font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 24)

# Draw app name
text_x = 80
text_y = 175

# Subtle text shadow
draw.text((text_x + 2, text_y + 2), "Vozcribe", fill=(0, 0, 0, 30), font=title_font)
draw.text((text_x, text_y), "Vozcribe", fill=(255, 255, 255, 255), font=title_font)

# Draw tagline
tagline_y = text_y + 90
draw.text((text_x + 1, tagline_y + 1), "Speak anywhere. Type nowhere.", fill=(0, 0, 0, 20), font=tagline_font)
draw.text((text_x, tagline_y), "Speak anywhere. Type nowhere.", fill=(255, 255, 255, 200), font=tagline_font)

# Convert to RGB (Play Store requires no alpha) and save as PNG
final = canvas.convert('RGB')
output_path = '/Users/ijas/Documents/whisperType/feature_graphic.png'
final.save(output_path, 'PNG')

# Verify
verify = Image.open(output_path)
print(f"Created: {output_path}")
print(f"Size: {verify.size[0]}x{verify.size[1]}")
print(f"Format: {verify.format}")
print(f"File size: {__import__('os').path.getsize(output_path)} bytes")
