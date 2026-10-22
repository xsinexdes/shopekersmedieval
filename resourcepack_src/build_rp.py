"""Genera el resourcepack medieval de Shopkeepers Medieval (MC 1.21.1, pack_format 34).

Incluye:
 - Ventana de comercio de aldeano/mercader reskineada (madera + pergamino).
 - Botones HD (CustomModelData 1001-1013 sobre PAPER).
 - Fondos para los menus de mercado / grafica / banco (glifos E000-E002).
Uso:  python3 build_rp.py   (necesita Pillow)
Salida: ../resourcepack/ShopkeepersMedieval.zip y la carpeta ../resourcepack/pack
"""
import os, json, zipfile, shutil, random, math
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'resourcepack')
PACK = os.path.join(OUT, 'pack')
VAN = os.path.join(HERE, 'vanilla')
shutil.rmtree(OUT, ignore_errors=True)
for d in ('assets/minecraft/models/item', 'assets/minecraft/font',
          'assets/minecraft/textures/gui/container',
          'assets/minecraft/textures/gui/sprites/container/villager',
          'assets/shopkeepers/models/item', 'assets/shopkeepers/textures/item',
          'assets/shopkeepers/textures/font'):
    os.makedirs(os.path.join(PACK, d))
RND = random.Random(1337)

# ----------------------------------------------------------------- paleta
INK = (34, 21, 11); WOOD_DD = (52, 32, 17); WOOD_D = (84, 54, 28); WOOD = (122, 82, 44)
WOOD_L = (165, 117, 66); GOLD = (244, 200, 72); GOLD_L = (255, 236, 150); GOLD_D = (158, 108, 24)
PARCH = (226, 205, 156); PARCH_L = (246, 233, 196); PARCH_D = (186, 160, 108)
RED = (176, 44, 44); RED_D = (110, 22, 26); GREEN = (70, 150, 66); GREEN_D = (34, 92, 38)
IRON = (150, 154, 168); IRON_D = (82, 86, 98); BLUE = (52, 98, 168)

def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def jitter(c, amt):
    n = RND.randint(-amt, amt)
    return tuple(max(0, min(255, v + n)) for v in c)

def wood_texture(w, h, base=WOOD, light=WOOD_L, dark=WOOD_D, plank=0):
    img = Image.new('RGBA', (w, h))
    px = img.load()
    rows = [RND.uniform(-1, 1) for _ in range(h)]
    for y in range(h):
        row_t = 0.5 + 0.5 * math.sin(y * 0.35 + rows[y]) * 0.5
        for x in range(w):
            t = row_t + 0.18 * math.sin((x + y * 3) * 0.05) + RND.uniform(-0.06, 0.06)
            t = max(0.0, min(1.0, t))
            c = mix(dark, base, t * 2) if t < 0.5 else mix(base, light, (t - 0.5) * 2)
            px[x, y] = c + (255,)
    if plank:
        d = ImageDraw.Draw(img)
        for y in range(plank, h, plank):
            d.line([0, y, w, y], fill=WOOD_DD + (255,), width=max(1, w // 80))
    return img

def parch_texture(w, h, light=None, dark=None):
    light = light or PARCH_L
    dark = dark or PARCH_D
    img = Image.new('RGBA', (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            dx = (x - w / 2) / (w / 2); dy = (y - h / 2) / (h / 2)
            v = min(1.0, (dx * dx + dy * dy) * 0.55)
            c = mix(light, dark, v)
            n = RND.randint(-5, 5)
            px[x, y] = (max(0, c[0] + n), max(0, c[1] + n), max(0, c[2] + n), 255)
    return img

def shadowed(layer_fn, size, offset, shadow=(20, 10, 4, 150), blur=0):
    """Dibuja layer_fn(draw, color_override) con sombra."""
    sh = Image.new('RGBA', size, (0, 0, 0, 0))
    layer_fn(ImageDraw.Draw(sh), shadow)
    if blur:
        sh = sh.filter(ImageFilter.GaussianBlur(blur))
    out = Image.new('RGBA', size, (0, 0, 0, 0))
    out.alpha_composite(sh, offset)
    top = Image.new('RGBA', size, (0, 0, 0, 0))
    layer_fn(ImageDraw.Draw(top), None)
    out.alpha_composite(top)
    return out

# ----------------------------------------------------------------- botones (256 -> 64)
S = 256

def button_base():
    img = wood_texture(S, S, plank=0)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, S - 1, S - 1], radius=26, outline=INK + (255,), width=8)
    d.rounded_rectangle([10, 10, S - 11, S - 11], radius=20, outline=GOLD_D + (255,), width=6)
    d.rounded_rectangle([16, 16, S - 17, S - 17], radius=16, outline=GOLD + (255,), width=4)
    d.line([30, 20, S - 30, 20], fill=GOLD_L + (255,), width=3)  # brillo superior
    d.line([20, 30, 20, S - 30], fill=GOLD_L + (200,), width=2)
    for (x, y) in [(34, 34), (S - 35, 34), (34, S - 35), (S - 35, S - 35)]:
        d.ellipse([x - 11, y - 11, x + 11, y + 11], fill=IRON_D + (255,), outline=INK + (255,), width=3)
        d.ellipse([x - 7, y - 8, x + 3, y + 2], fill=IRON + (255,))
    # sombra interior para dar profundidad
    inner = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(inner).rounded_rectangle([26, 26, S - 27, S - 27], radius=14, fill=(0, 0, 0, 55))
    img.alpha_composite(inner)
    return img

def P(pts, dx=0, dy=0):
    return [(x + dx, y + dy) for x, y in pts]

def col(c, ov):
    return ov if ov is not None else c + (255,)

def coin_shape(d, cx, cy, r, ov):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=col(GOLD_D, ov))
    d.ellipse([cx - r + 5, cy - r + 5, cx + r - 5, cy + r - 5], fill=col(GOLD, ov))
    if ov is None:
        d.ellipse([cx - r + 13, cy - r + 13, cx + r - 13, cy + r - 13], outline=GOLD_D + (255,), width=5)
        d.arc([cx - r + 8, cy - r + 8, cx + r - 8, cy + r - 8], 200, 270, fill=GOLD_L + (255,), width=6)
        d.polygon([(cx, cy - r * 0.38), (cx + r * 0.3, cy), (cx, cy + r * 0.38), (cx - r * 0.3, cy)],
                  fill=GOLD_D + (255,))

def ico_arrow(direction):
    def fn(d, ov):
        if direction == 'left':
            pts = [(54, 128), (120, 66), (120, 104), (200, 104), (200, 152), (120, 152), (120, 190)]
        else:
            pts = [(202, 128), (136, 66), (136, 104), (56, 104), (56, 152), (136, 152), (136, 190)]
        d.polygon(pts, fill=col(PARCH_L, ov))
        if ov is None:
            d.line(pts + [pts[0]], fill=INK + (255,), width=5)
    return fn

def ico_close(d, ov):
    for w, c in ((34, RED_D), (22, RED)):
        d.line([76, 76, 180, 180], fill=col(c, ov), width=w)
        d.line([180, 76, 76, 180], fill=col(c, ov), width=w)
    if ov is None:
        d.line([88, 84, 160, 156], fill=(240, 120, 110, 255), width=5)

def ico_clock(d, ov):
    d.ellipse([54, 54, 202, 202], fill=col(GOLD_D, ov))
    d.ellipse([66, 66, 190, 190], fill=col(PARCH_L, ov))
    if ov is None:
        for a in range(12):
            r1, r2 = (68, 82) if a % 3 else (62, 82)
            x1 = 128 + r1 * math.sin(a * math.pi / 6); y1 = 128 - r1 * math.cos(a * math.pi / 6)
            x2 = 128 + r2 * math.sin(a * math.pi / 6); y2 = 128 - r2 * math.cos(a * math.pi / 6)
            d.line([x1, y1, x2, y2], fill=INK + (255,), width=4)
        d.line([128, 128, 128, 84], fill=INK + (255,), width=8)
        d.line([128, 128, 164, 146], fill=RED_D + (255,), width=7)
        d.ellipse([120, 120, 136, 136], fill=GOLD_D + (255,))

def ico_refresh(d, ov):
    d.arc([60, 60, 196, 196], 40, 330, fill=col(PARCH_L, ov), width=26)
    d.polygon([(184, 48), (214, 118), (146, 108)], fill=col(PARCH_L, ov))

def ico_deposit(d, ov):
    coin_shape(d, 128, 92, 58, ov)
    d.polygon([(128, 232), (76, 170), (110, 170), (110, 146), (146, 146), (146, 170), (180, 170)], fill=col(GREEN, ov))
    if ov is None:
        d.line([(128, 232), (76, 170), (110, 170), (110, 146), (146, 146), (146, 170), (180, 170), (128, 232)],
               fill=GREEN_D + (255,), width=5)

def ico_withdraw(d, ov):
    coin_shape(d, 128, 164, 58, ov)
    pts = [(128, 24), (76, 86), (110, 86), (110, 110), (146, 110), (146, 86), (180, 86)]
    d.polygon(pts, fill=col(RED, ov))
    if ov is None:
        d.line(pts + [pts[0]], fill=RED_D + (255,), width=5)

def ico_coins(d, ov):
    coin_shape(d, 92, 168, 46, ov)
    coin_shape(d, 164, 168, 46, ov)
    coin_shape(d, 128, 96, 52, ov)

def ico_scroll(d, ov):
    d.rounded_rectangle([76, 56, 180, 204], radius=8, fill=col(PARCH, ov))
    d.rounded_rectangle([60, 44, 196, 72], radius=14, fill=col(PARCH_D, ov))
    d.rounded_rectangle([60, 186, 196, 214], radius=14, fill=col(PARCH_D, ov))
    if ov is None:
        for y in (92, 116, 140, 164):
            d.line([94, y, 162, y], fill=INK + (255,), width=5)
        d.ellipse([112, 168, 144, 200], fill=RED + (255,), outline=RED_D + (255,), width=4)

def ico_banner(d, ov):
    d.line([84, 44, 84, 216], fill=col(PARCH_D, ov), width=12)
    d.ellipse([74, 32, 96, 54], fill=col(GOLD, ov))
    pts = [(92, 56), (200, 66), (200, 168), (146, 148), (92, 168)]
    d.polygon(pts, fill=col(RED, ov))
    if ov is None:
        d.line(pts + [pts[0]], fill=RED_D + (255,), width=6)
        d.polygon([(146, 84), (166, 112), (146, 140), (126, 112)], fill=GOLD + (255,))

def ico_market(d, ov):
    d.polygon([(48, 112), (74, 56), (182, 56), (208, 112)], fill=col(RED, ov))
    if ov is None:
        for i, x in enumerate(range(58, 208, 38)):
            if i % 2 == 0:
                d.polygon([(x, 112), (x + 12, 56), (x + 38, 56), (x + 38, 112)], fill=PARCH_L + (255,))
        d.line([(48, 112), (74, 56), (182, 56), (208, 112), (48, 112)], fill=RED_D + (255,), width=5)
    d.rectangle([62, 112, 194, 206], fill=col(PARCH, ov))
    if ov is None:
        d.rectangle([110, 146, 146, 206], fill=WOOD_D + (255,), outline=INK + (255,), width=4)
        d.rectangle([74, 124, 100, 146], fill=GOLD + (255,), outline=GOLD_D + (255,), width=3)
        d.rectangle([156, 124, 182, 146], fill=GREEN + (255,), outline=GREEN_D + (255,), width=3)

def ico_bank(d, ov):
    d.polygon([(40, 98), (128, 40), (216, 98)], fill=col(GOLD, ov))
    for x in (66, 106, 146, 186):
        d.rectangle([x - 12, 106, x + 12, 186], fill=col(PARCH_L, ov))
        if ov is None:
            d.rectangle([x - 12, 106, x + 12, 186], outline=INK + (255,), width=3)
    d.rectangle([44, 190, 212, 214], fill=col(GOLD, ov))
    d.rectangle([34, 214, 222, 230], fill=col(GOLD_D, ov))
    if ov is None:
        d.line([(40, 98), (128, 40), (216, 98), (40, 98)], fill=GOLD_D + (255,), width=5)
        d.ellipse([116, 70, 140, 94], fill=RED + (255,), outline=RED_D + (255,), width=3)

BUTTONS = [
    ('btn_back', ico_arrow('left')), ('btn_next', ico_arrow('right')), ('btn_prev', ico_arrow('left')),
    ('btn_close', ico_close), ('btn_clock', ico_clock), ('btn_refresh', ico_refresh),
    ('btn_deposit', ico_deposit), ('btn_withdraw', ico_withdraw), ('btn_coins', ico_coins),
    ('btn_scroll', ico_scroll), ('btn_banner', ico_banner), ('btn_market', ico_market), ('btn_bank', ico_bank),
]
BASE = button_base()
overrides = []
sheet = Image.new('RGBA', (64 * 7, 64 * 2), (40, 40, 40, 255))
for i, (name, fn) in enumerate(BUTTONS):
    img = BASE.copy()
    img.alpha_composite(shadowed(fn, (S, S), (6, 8), blur=3))
    small = img.resize((64, 64), Image.LANCZOS)
    small.save(f'{PACK}/assets/shopkeepers/textures/item/{name}.png')
    sheet.alpha_composite(small, ((i % 7) * 64, (i // 7) * 64))
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'shopkeepers:item/{name}'}},
              open(f'{PACK}/assets/shopkeepers/models/item/{name}.json', 'w'))
    overrides.append({'predicate': {'custom_model_data': 1001 + i}, 'model': f'shopkeepers:item/{name}'})
json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'minecraft:item/paper'}, 'overrides': overrides},
          open(f'{PACK}/assets/minecraft/models/item/paper.json', 'w'), indent=1)
sheet.resize((64 * 7 * 2, 64 * 2 * 2), Image.NEAREST).save('/tmp/prev_buttons.png')

# ----------------------------------------------------------------- fondos de menus (512 px = 2x)
K = 2  # factor de escala; el glifo se dibuja a 256 de alto (height) -> nitido en GUI grande

def bg(name, accent, accent_d, frame=None, area=None, slot=None, emblem=None):
    frame = frame or (WOOD, WOOD_L, WOOD_D)
    area = area or (PARCH_L, PARCH_D)
    slot = slot or ((150, 122, 78), (192, 166, 116))
    W = 256 * K
    img = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    def r(x0, y0, x1, y1):
        return [x0 * K, y0 * K, x1 * K - 1, y1 * K - 1]
    # marco de madera
    img.alpha_composite(wood_texture(176 * K, 222 * K, base=frame[0], light=frame[1], dark=frame[2]), (0, 0))
    d.rectangle(r(0, 0, 176, 222), outline=INK + (255,), width=2 * K)
    d.rectangle(r(2, 2, 174, 220), outline=GOLD_D + (255,), width=K)
    d.rectangle(r(3, 3, 173, 219), outline=GOLD + (255,), width=K)
    # cinta del titulo
    d.rectangle(r(5, 5, 171, 16), fill=PARCH_L + (255,), outline=accent_d + (255,), width=K)
    d.rectangle(r(5, 14, 171, 16), fill=accent + (255,))
    # zona del cofre en pergamino
    img.alpha_composite(parch_texture(166 * K, 118 * K, area[0], area[1]), (5 * K, 17 * K))
    d.rectangle(r(5, 17, 171, 135), outline=accent_d + (255,), width=K)
    # rejilla de rombos muy tenue
    lat = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    ld = ImageDraw.Draw(lat)
    for o in range(-200, 400, 14):
        ld.line([o * K, 17 * K, (o + 118) * K, 135 * K], fill=(120, 90, 50, 28), width=1)
        ld.line([o * K, 135 * K, (o + 118) * K, 17 * K], fill=(120, 90, 50, 28), width=1)
    mask = Image.new('L', (W, W), 0)
    ImageDraw.Draw(mask).rectangle(r(6, 18, 170, 134), fill=255)
    img.alpha_composite(Image.composite(lat, Image.new('RGBA', (W, W), (0, 0, 0, 0)), mask))
    # slots del cofre (grabados)
    for rr in range(6):
        for c in range(9):
            x, y = 7 + 18 * c, 17 + 18 * rr
            d.rectangle(r(x, y, x + 18, y + 18), fill=slot[0] + (255,))
            d.rectangle(r(x + 1, y + 1, x + 17, y + 17), fill=slot[1] + (255,))
            d.line([x * K, y * K, (x + 18) * K, y * K], fill=(96, 72, 40, 255), width=K)
            d.line([x * K, y * K, x * K, (y + 18) * K], fill=(96, 72, 40, 255), width=K)
            d.line([x * K, (y + 18) * K - 1, (x + 18) * K, (y + 18) * K - 1], fill=(244, 228, 186, 255), width=K)
            d.line([(x + 18) * K - 1, y * K, (x + 18) * K - 1, (y + 18) * K], fill=(244, 228, 186, 255), width=K)
    # separador con rombo dorado
    d.rectangle(r(5, 135, 171, 139), fill=WOOD_DD + (255,))
    d.line([8 * K, 137 * K, 168 * K, 137 * K], fill=GOLD + (255,), width=K)
    cx, cy = 88 * K, 137 * K
    d.polygon([(cx, cy - 4 * K), (cx + 6 * K, cy), (cx, cy + 4 * K), (cx - 6 * K, cy)], fill=GOLD + (255,), outline=INK + (255,))
    # inventario del jugador
    img.alpha_composite(wood_texture(166 * K, 78 * K, base=frame[2], light=frame[0], dark=tuple(int(c * 0.55) for c in frame[2])), (5 * K, 139 * K))
    for rr in range(3):
        for c in range(9):
            x, y = 7 + 18 * c, 139 + 18 * rr
            d.rectangle(r(x, y, x + 18, y + 18), fill=(52, 34, 18, 255), outline=(28, 17, 9, 255), width=K)
            d.line([x * K + K, (y + 18) * K - 1, (x + 18) * K - K, (y + 18) * K - 1], fill=(120, 86, 48, 255), width=K)
    for c in range(9):
        x, y = 7 + 18 * c, 197
        d.rectangle(r(x, y, x + 18, y + 18), fill=(52, 34, 18, 255), outline=(28, 17, 9, 255), width=K)
        d.line([x * K + K, (y + 18) * K - 1, (x + 18) * K - K, (y + 18) * K - 1], fill=(120, 86, 48, 255), width=K)
    # esquinas ornamentales
    for (x, y) in [(4, 4), (172, 4), (4, 218), (172, 218)]:
        d.ellipse([(x - 3) * K, (y - 3) * K, (x + 3) * K, (y + 3) * K], fill=GOLD + (255,), outline=INK + (255,), width=1)
    if emblem:
        emblem(d)
    img.save(f'{PACK}/assets/shopkeepers/textures/font/{name}.png')
    return img

# --- emblemas pequenos para la cinta del titulo (esquina derecha), coordenadas en 1x
def E(pts):
    return [(x * K, y * K) for x, y in pts]

def em_anvil(d):
    d.polygon(E([(152, 7), (170, 7), (170, 9), (166, 11), (166, 13), (156, 13), (156, 11), (152, 9)]), fill=(70, 74, 86, 255), outline=INK + (255,))
    d.polygon(E([(148, 8), (153, 8), (153, 9)]), fill=(70, 74, 86, 255))

def em_wheat(d):
    d.line(E([(160, 14), (160, 6)]), fill=(150, 120, 40, 255), width=K)
    for dy in (7, 9, 11):
        d.ellipse([157 * K, dy * K, 160 * K, (dy + 2) * K], fill=GOLD + (255,))
        d.ellipse([160 * K, dy * K, 163 * K, (dy + 2) * K], fill=GOLD + (255,))

def em_flask(d):
    d.polygon(E([(158, 6), (162, 6), (162, 9), (166, 14), (154, 14), (158, 9)]), fill=(150, 60, 190, 255), outline=INK + (255,))
    d.rectangle(r_(157, 5, 163, 6), fill=(230, 220, 240, 255))

def em_gem(d):
    d.polygon(E([(160, 6), (166, 9), (160, 14), (154, 9)]), fill=(70, 190, 230, 255), outline=INK + (255,))
    d.line(E([(154, 9), (166, 9)]), fill=(200, 240, 255, 255), width=1)

def em_brick(d):
    for (x, y, w) in [(152, 6, 8), (161, 6, 8), (156, 10, 8), (148, 10, 7), (165, 10, 6)]:
        d.rectangle([x * K, y * K, (x + w) * K, (y + 3) * K], fill=(190, 100, 70, 255), outline=INK + (255,))

def em_star(d):
    cx, cy = 160, 10
    pts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rad = 5 if i % 2 == 0 else 2.2
        pts.append(((cx + rad * math.cos(ang)) * K, (cy + rad * math.sin(ang)) * K))
    d.polygon(pts, fill=GOLD + (255,), outline=GOLD_D + (255,))

def r_(x0, y0, x1, y1):
    return [x0 * K, y0 * K, x1 * K - 1, y1 * K - 1]

THEMES = {
    'bg_market': dict(accent=(176, 44, 44), accent_d=RED_D, emblem=em_star),
    'bg_graph': dict(accent=(52, 98, 168), accent_d=(28, 52, 96)),
    'bg_bank': dict(accent=GOLD, accent_d=GOLD_D, emblem=em_star),
    'bg_forja': dict(accent=(230, 90, 20), accent_d=(120, 40, 10), frame=((72, 74, 86), (120, 122, 136), (36, 37, 46)),
                     area=((214, 198, 184), (150, 132, 120)), slot=((126, 106, 96), (176, 156, 142)), emblem=em_anvil),
    'bg_granja': dict(accent=(70, 150, 60), accent_d=(30, 90, 30), frame=((92, 112, 56), (128, 154, 78), (50, 66, 30)),
                      area=((228, 234, 190), (178, 194, 130)), slot=((140, 156, 96), (190, 206, 140)), emblem=em_wheat),
    'bg_alquimia': dict(accent=(150, 60, 190), accent_d=(80, 30, 110), frame=((88, 54, 116), (128, 88, 160), (46, 26, 64)),
                        area=((234, 216, 242), (188, 162, 208)), slot=((150, 124, 170), (200, 178, 220)), emblem=em_flask),
    'bg_minerales': dict(accent=(50, 150, 200), accent_d=(24, 80, 120), frame=((90, 102, 120), (132, 146, 166), (48, 56, 70)),
                         area=((216, 228, 238), (162, 180, 198)), slot=((130, 146, 166), (182, 198, 214)), emblem=em_gem),
    'bg_construccion': dict(accent=(200, 130, 40), accent_d=(120, 70, 20), frame=((142, 86, 62), (178, 114, 82), (84, 48, 34)),
                            area=((234, 218, 186), (188, 166, 126)), slot=((156, 132, 96), (204, 182, 140)), emblem=em_brick),
    'bg_varios': dict(accent=GOLD, accent_d=GOLD_D, emblem=em_star),
}
bgs = [bg(n, **t) for n, t in THEMES.items()]
pv = Image.new('RGBA', (5 * 270, 2 * 340), (60, 60, 60, 255))
for i, b_ in enumerate(bgs):
    pv.alpha_composite(b_.crop((0, 0, 352, 444)).resize((264, 333)), ((i % 5) * 270 + 3, (i // 5) * 340 + 3))
pv.save('/tmp/prev_bgs.png')

# ----------------------------------------------------------------- ventana de comercio (recolor)
def gradient_map(img, stops, texture=True, lattice=False):
    img = img.convert('RGBA')
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0 or max(r, g, b) - min(r, g, b) > 14:
                continue
            l = (r + g + b) / 3.0
            for i in range(len(stops) - 1):
                l0, c0 = stops[i]; l1, c1 = stops[i + 1]
                if l <= l1:
                    t = (l - l0) / max(1.0, (l1 - l0))
                    c = mix(c0, c1, max(0.0, min(1.0, t)))
                    break
            else:
                c = stops[-1][1]
            if texture and 132 <= l <= 146:  # lista de trades: veta de madera
                n = int(7 * math.sin(y * 0.9 + (x // 24) * 1.7)) + RND.randint(-4, 4)
                c = (c[0] + n, c[1] + n, c[2] + n)
            if texture and 190 <= l <= 205:  # color base del panel
                n = RND.randint(-6, 6)
                c = (c[0] + n, c[1] + n, c[2] + n)
                if lattice and ((x + y) % 14 == 0 or (x - y) % 14 == 0):
                    c = (c[0] - 14, c[1] - 14, c[2] - 12)
            px[x, y] = (max(0, min(255, c[0])), max(0, min(255, c[1])), max(0, min(255, c[2])), a)
    return img

STOPS = [(0, (30, 18, 9)), (55, (74, 46, 24)), (90, (96, 64, 34)), (139, (132, 98, 60)),
         (198, (218, 196, 146)), (255, (252, 240, 204))]
vil = gradient_map(Image.open(f'{VAN}/villager.png'), STOPS, texture=True, lattice=True)
vd = ImageDraw.Draw(vil)
for (x, y) in [(272, 4), (3, 162), (272, 162)]:   # tachuelas doradas en el marco
    vd.ellipse([x - 2, y - 2, x + 1, y + 1], fill=GOLD + (255,), outline=INK + (255,))
vil.save(f'{PACK}/assets/minecraft/textures/gui/container/villager.png')

SPR = f'{PACK}/assets/minecraft/textures/gui/sprites/container/villager'
for n in ('scroller', 'scroller_disabled', 'experience_bar_background', 'trade_arrow', 'trade_arrow_out_of_stock'):
    im = gradient_map(Image.open(f'{VAN}/s_{n}.png'), STOPS, texture=False)
    if n == 'trade_arrow':
        px = im.load()
        for y in range(im.size[1]):
            for x in range(im.size[0]):
                if px[x, y][3]:
                    px[x, y] = GOLD_D + (255,) if x % 2 else GOLD + (255,)
    if n == 'scroller':
        px = im.load()
        for y in range(im.size[1]):
            for x in range(im.size[0]):
                if px[x, y][3]:
                    px[x, y] = mix(GOLD_D, GOLD_L, 0.2 + 0.8 * (1 - x / 6.0)) + (255,)
    im.save(f'{SPR}/{n}.png')
shutil.copy(f'{VAN}/s_experience_bar_current.png', f'{SPR}/experience_bar_current.png')
shutil.copy(f'{VAN}/s_experience_bar_result.png', f'{SPR}/experience_bar_result.png')
shutil.copy(f'{VAN}/s_out_of_stock.png', f'{SPR}/out_of_stock.png')
shutil.copy(f'{VAN}/s_discount_strikethrough.png', f'{SPR}/discount_strikethrough.png')
vp = Image.new('RGBA', (300, 180), (60, 60, 60, 255)); vp.alpha_composite(vil.crop((0, 0, 276, 166)), (2, 2))
vp.resize((900, 540), Image.NEAREST).save('/tmp/prev_villager.png')

# ----------------------------------------------------------------- sonidos (sintetizados)
def build_sounds():
    try:
        import numpy as np
        import soundfile as sf
    except ImportError:
        print('AVISO: sin numpy/soundfile, no se generan sonidos (pip install numpy soundfile)')
        return
    SR = 44100
    rng = np.random.default_rng(7)
    os.makedirs(f'{PACK}/assets/shopkeepers/sounds/ui', exist_ok=True)
    os.makedirs(f'{PACK}/assets/shopkeepers/sounds/trade', exist_ok=True)
    os.makedirs(f'{PACK}/assets/shopkeepers/sounds/event', exist_ok=True)

    def tt(d):
        return np.linspace(0, d, int(SR * d), endpoint=False)

    def sine(f, d, decay, amp=1.0, attack=0.002):
        x = tt(d)
        e = np.exp(-x * decay) * np.minimum(1.0, x / attack)
        return amp * e * np.sin(2 * np.pi * f * x)

    def clink(f, d=0.6, amp=1.0, decay=7.0):
        parts = [(1.0, 1.0, decay), (2.76, 0.55, decay * 1.3), (5.4, 0.35, decay * 1.8), (8.93, 0.2, decay * 2.5)]
        out = np.zeros(int(SR * d))
        for mult, a, dec in parts:
            out += sine(f * mult, d, dec, a * amp)
        return out

    def noise(d, lp=0.0):
        n = rng.standard_normal(int(SR * d))
        if lp > 0:
            k = max(1, int(lp))
            n = np.convolve(n, np.ones(k) / k, mode='same')
        return n

    def put(buf, sig, at):
        i = int(at * SR)
        j = min(len(buf), i + len(sig))
        if j > i:
            buf[i:j] += sig[:j - i]

    def finish(name, buf, peak=0.8):
        m = np.max(np.abs(buf)) or 1.0
        buf = buf / m * peak
        sf.write(f'{PACK}/assets/shopkeepers/sounds/{name}.ogg', buf.astype('float32'), SR, format='OGG', subtype='VORBIS')

    # ui/click: golpecito de madera
    b = np.zeros(int(SR * 0.14))
    put(b, sine(1500, 0.14, 45, 1.0), 0); put(b, sine(2700, 0.1, 80, 0.4), 0); put(b, noise(0.02, 4) * 0.35, 0)
    finish('ui/click', b)
    # ui/page: hoja de pergamino
    n = noise(0.4, 6); x = tt(0.4)
    b = n * np.sin(np.pi * x / 0.4) ** 2
    finish('ui/page', b, 0.6)
    # ui/coin: dos monedas
    b = np.zeros(int(SR * 0.8)); put(b, clink(3300, 0.7), 0); put(b, clink(4200, 0.6, 0.8), 0.09)
    finish('ui/coin', b)
    # ui/deposit: bolsa que cae + lluvia de monedas
    b = np.zeros(int(SR * 1.2)); put(b, sine(105, 0.3, 14, 1.2), 0); put(b, noise(0.12, 25) * 0.5, 0)
    for i in range(8):
        put(b, clink(float(rng.uniform(2400, 4300)), 0.5, 0.9 - i * 0.08), 0.08 + i * 0.075 + float(rng.uniform(0, 0.03)))
    finish('ui/deposit', b)
    # ui/withdraw: monedas que suben de tono + deslizamiento
    b = np.zeros(int(SR * 1.0)); put(b, noise(0.3, 12) * 0.25, 0)
    for i in range(6):
        put(b, clink(2400 + i * 330, 0.5, 0.6 + i * 0.08), 0.05 + i * 0.08)
    finish('ui/withdraw', b)
    # ui/error: dos golpes graves
    b = np.zeros(int(SR * 0.55)); put(b, sine(165, 0.3, 11, 1.0), 0); put(b, sine(124, 0.35, 10, 1.0), 0.17)
    put(b, noise(0.03, 8) * 0.4, 0); put(b, noise(0.03, 8) * 0.4, 0.17)
    finish('ui/error', b)
    # trade/success: moneda + campanilla en arpegio
    b = np.zeros(int(SR * 1.1)); put(b, clink(2900, 0.5, 0.8), 0)
    for i, f in enumerate((1046.5, 1318.5, 1568.0, 2093.0)):
        put(b, sine(f, 0.9, 5.5, 0.55), 0.06 + i * 0.07)
        put(b, sine(f * 2.01, 0.6, 9, 0.12), 0.06 + i * 0.07)
    finish('trade/success', b)
    # event/horn: cuerno de bronce, 4 notas
    def brass(f, d):
        x = tt(d)
        vib = 1 + 0.004 * np.sin(2 * np.pi * 5.2 * x)
        sig = np.zeros_like(x)
        for h in range(1, 9):
            sig += (1.0 / h ** 0.9) * np.sin(2 * np.pi * f * h * x * vib)
        e = np.minimum(1.0, x / 0.06) * np.minimum(1.0, (d - x) / 0.12)
        return sig * e
    b = np.zeros(int(SR * 2.6))
    for f, at, d in ((196.0, 0.0, 0.55), (261.6, 0.5, 0.55), (329.6, 1.0, 0.5), (392.0, 1.45, 0.95)):
        put(b, brass(f, d), at)
    for delay, g in ((0.11, 0.4), (0.23, 0.25), (0.37, 0.15)):   # eco / sala
        d_ = np.roll(b, int(delay * SR)) * g
        b = b + d_
    finish('event/horn', b)
    # event/bell: campana
    b = np.zeros(int(SR * 3.0))
    for mult, a, dec in ((1.0, 1.0, 1.4), (2.0, 0.6, 1.9), (2.76, 0.55, 2.3), (5.4, 0.3, 3.2), (8.93, 0.2, 4.2)):
        put(b, sine(392.0 * mult, 3.0, dec, a, 0.001), 0)
    finish('event/bell', b)

    events = {
        'ui.click': 'ui/click', 'ui.page': 'ui/page', 'ui.coin': 'ui/coin', 'ui.deposit': 'ui/deposit',
        'ui.withdraw': 'ui/withdraw', 'ui.error': 'ui/error', 'trade.success': 'trade/success',
        'event.horn': 'event/horn', 'event.bell': 'event/bell',
    }
    data = {k: {'sounds': [{'name': f'shopkeepers:{v}', 'volume': 1.0}]} for k, v in events.items()}
    json.dump(data, open(f'{PACK}/assets/shopkeepers/sounds.json', 'w'), indent=1)
    print('sonidos ok')

build_sounds()

# ----------------------------------------------------------------- skins de mercaderes (aldeanos)
ENT = os.path.join(VAN, 'ent')

def tint_map(img, dark, mid, light):
    img = img.convert('RGBA')
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            l = 0.299 * r + 0.587 * g + 0.114 * b
            if l < 128:
                c = mix(dark, mid, l / 128.0)
            else:
                c = mix(mid, light, (l - 128) / 127.0)
            px[x, y] = (c[0], c[1], c[2], a)
    return img

PROF = {   # (oscuro, medio, claro)  -> cada oficio con colores de su gremio
    'armorer': ((22, 26, 36), (96, 106, 126), (206, 214, 228)),
    'weaponsmith': ((38, 10, 12), (128, 36, 34), (232, 168, 130)),
    'toolsmith': ((40, 22, 8), (150, 82, 30), (240, 186, 96)),
    'butcher': ((46, 8, 12), (156, 30, 38), (240, 176, 176)),
    'farmer': ((62, 44, 10), (176, 134, 40), (252, 226, 124)),
    'fisherman': ((8, 30, 46), (30, 108, 128), (170, 228, 232)),
    'fletcher': ((12, 34, 16), (50, 118, 52), (176, 224, 146)),
    'leatherworker': ((48, 26, 10), (152, 102, 54), (230, 196, 144)),
    'librarian': ((14, 18, 48), (52, 76, 160), (190, 208, 248)),
    'cleric': ((30, 8, 40), (118, 42, 132), (252, 214, 108)),
    'mason': ((38, 36, 34), (128, 122, 110), (226, 220, 204)),
    'shepherd': ((48, 36, 24), (150, 124, 94), (248, 242, 224)),
    'cartographer': ((24, 38, 32), (86, 124, 96), (238, 228, 178)),
    'nitwit': ((38, 46, 16), (120, 140, 50), (234, 234, 150)),
}
TYPES = {
    'plains': ((34, 20, 12), (112, 68, 38), (206, 156, 104)),
    'desert': ((60, 42, 16), (184, 134, 52), (246, 218, 144)),
    'jungle': ((10, 38, 20), (40, 122, 62), (164, 224, 144)),
    'savanna': ((58, 22, 12), (172, 82, 42), (242, 174, 112)),
    'snow': ((38, 50, 68), (130, 162, 194), (238, 246, 255)),
    'swamp': ((26, 32, 16), (82, 102, 52), (194, 204, 134)),
    'taiga': ((32, 28, 22), (112, 94, 72), (224, 208, 182)),
}
for sub, table, prefix in (('profession', PROF, 'p_'), ('type', TYPES, 't_')):
    os.makedirs(f'{PACK}/assets/minecraft/textures/entity/villager/{sub}', exist_ok=True)
    for name, (dk, md, lt) in table.items():
        src = os.path.join(ENT, f'{prefix}{name}.png')
        if os.path.exists(src):
            tint_map(Image.open(src), dk, md, lt).save(f'{PACK}/assets/minecraft/textures/entity/villager/{sub}/{name}.png')
sheet = Image.new('RGBA', (7 * 140, 4 * 140), (58, 58, 74, 255))
i = 0
for sub, table in (('profession', PROF), ('type', TYPES)):
    for name in table:
        f = f'{PACK}/assets/minecraft/textures/entity/villager/{sub}/{name}.png'
        if os.path.exists(f):
            sheet.alpha_composite(Image.open(f).convert('RGBA').resize((128, 128), Image.NEAREST), ((i % 7) * 140 + 6, (i // 7) * 140 + 6))
            i += 1
sheet.save('/tmp/prev_skins.png')

# ----------------------------------------------------------------- font, pack.mcmeta, pack.png
spaces = {chr(0xF800 + n): -n for n in (1, 2, 4, 8, 16, 32, 64, 128)}
names = ['bg_market', 'bg_graph', 'bg_bank', 'bg_forja', 'bg_granja', 'bg_alquimia', 'bg_minerales',
         'bg_construccion', 'bg_varios']
providers = [{'type': 'bitmap', 'file': f'shopkeepers:font/{n}.png', 'ascent': 13, 'height': 256,
              'chars': [chr(0xE000 + i)]} for i, n in enumerate(names)]
providers.append({'type': 'space', 'advances': spaces})
font = {'providers': providers}
json.dump(font, open(f'{PACK}/assets/minecraft/font/default.json', 'w'), indent=1)
json.dump({'pack': {'pack_format': 34, 'description': '\u00a76Shopkeepers Medieval \u00a77- madera, pergamino y oro (1.21.1)'}},
          open(f'{PACK}/pack.mcmeta', 'w'), indent=1)
icon = BASE.copy(); icon.alpha_composite(shadowed(ico_coins, (S, S), (6, 8), blur=3))
icon.resize((128, 128), Image.LANCZOS).save(f'{PACK}/pack.png')

zpath = os.path.join(OUT, 'ShopkeepersMedieval.zip')
with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(PACK):
        for f in files:
            full = os.path.join(root, f)
            z.write(full, os.path.relpath(full, PACK))
print('ok', zpath, os.path.getsize(zpath) // 1024, 'KB')
