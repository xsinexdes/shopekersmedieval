"""Genera el resourcepack medieval de Shopkeepers Medieval (MC 1.21.1, pack_format 34).

Incluye:
 - Ventana de comercio de aldeano/mercader reskineada (madera + pergamino).
 - Botones HD (CustomModelData 1001-1013 sobre PAPER).
 - Fondos para los menus de mercado / grafica / banco (glifos E000-E002).
Uso:  python3 build_rp.py   (necesita Pillow)
Salida: ../resourcepack/ShopkeepersMedieval.zip y la carpeta ../resourcepack/pack
"""
import os, sys, json, zipfile, shutil, random, math
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gui_dark as gd
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
    """Boton de un slot en estilo oscuro-dorado (vidrio oscuro con borde dorado y brillo)."""
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    px = img.load()
    for y in range(S):
        for x in range(S):
            t = y / (S - 1)
            c = mix((34, 30, 40), (10, 9, 13), t)
            px[x, y] = c + (255,)
    mask = Image.new('L', (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=28, fill=255)
    img.putalpha(mask)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, S - 1, S - 1], radius=28, outline=(70, 52, 14, 255), width=8)
    d.rounded_rectangle([9, 9, S - 10, S - 10], radius=22, outline=(212, 175, 55, 255), width=5)
    d.line([34, 17, S - 34, 17], fill=(255, 232, 140, 200), width=3)
    glow_l = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(glow_l).ellipse([48, 40, S - 48, S - 24], fill=(212, 175, 55, 40))
    img.alpha_composite(glow_l.filter(ImageFilter.GaussianBlur(18)))
    for (x, y) in [(30, 30), (S - 31, 30), (30, S - 31), (S - 31, S - 31)]:
        d.ellipse([x - 7, y - 7, x + 7, y + 7], fill=(255, 232, 140, 255), outline=(120, 94, 26, 255), width=2)
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
Image.new('RGBA', (16, 16), (0, 0, 0, 0)).save(f'{PACK}/assets/shopkeepers/textures/item/hit.png')
json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'shopkeepers:item/hit'}},
          open(f'{PACK}/assets/shopkeepers/models/item/hit.json', 'w'))
overrides.append({'predicate': {'custom_model_data': 2000}, 'model': 'shopkeepers:item/hit'})
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

# --- fondos oscuro-dorado: listas con items (E000-E009) y menus de botones anchos (E010+)
LIST_ACCENTS = {
    'bg_market': 'turquesa', 'bg_graph': 'azul', 'bg_bank': 'dorado', 'bg_forja': 'rojo', 'bg_granja': 'verde2',
    'bg_alquimia': 'morado', 'bg_minerales': 'celeste', 'bg_construccion': 'marron', 'bg_varios': 'dorado',
    'bg_config': 'gris',
}
names = list(LIST_ACCENTS.keys())
FONT_DIR = f'{PACK}/assets/shopkeepers/textures/font'
bgs = []
for n_ in names:
    cv = gd.render_list(gd.C[LIST_ACCENTS[n_]])
    cv.save(f'{FONT_DIR}/{n_}.png', optimize=True)
    bgs.append(cv)
layout_chars = {}
for key, spec_ in gd.LAYOUTS.items():
    cv = gd.render_layout(key, spec_)
    cv.save(f'{FONT_DIR}/{spec_["bg"]}.png', optimize=True)
    names.append(spec_['bg'])
    layout_chars[spec_['bg']] = chr(0xE000 + len(names) - 1)
    bgs.append(cv)
open(os.path.join(HERE, '..', 'src', 'main', 'java', 'me', 'medieval', 'shops', 'gui', 'Layouts.java'), 'w', encoding='utf-8') \
    .write(gd.java_source(layout_chars))
pv = Image.new('RGBA', (5 * 270, 3 * 340), (60, 60, 60, 255))
for i_, b_ in enumerate(bgs):
    pv.alpha_composite(b_.crop((0, 0, gd.S(176), gd.S(222))).resize((264, 333)), ((i_ % 5) * 270 + 3, (i_ // 5) * 340 + 3))
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
        from scipy import signal
    except ImportError:
        print('AVISO: faltan numpy/soundfile/scipy, no se generan sonidos (pip install numpy soundfile scipy)')
        return
    SR = 44100
    rng = np.random.default_rng(2024)
    for d in ('ui', 'trade', 'event'):
        os.makedirs(f'{PACK}/assets/shopkeepers/sounds/{d}', exist_ok=True)

    def tt(d):
        return np.arange(int(SR * d)) / SR

    def modal(f, d, decay, amp=1.0, phase=0.0):
        x = tt(d)
        return amp * np.exp(-x * decay) * np.sin(2 * np.pi * f * x + phase)

    def sos(kind, cutoff, order=2):
        return signal.butter(order, cutoff, kind, fs=SR, output='sos')

    def lp(x, f, order=2):
        return signal.sosfilt(sos('low', f, order), x)

    def hp(x, f, order=2):
        return signal.sosfilt(sos('high', f, order), x)

    def bp(x, lo, hi, order=2):
        return signal.sosfilt(sos('band', [lo, hi], order), x)

    def noise(d):
        return rng.standard_normal(int(SR * d))

    def put(buf, sig, at, gain=1.0):
        i = int(at * SR)
        j = min(len(buf), i + len(sig))
        if j > i:
            buf[i:j] += gain * sig[:j - i]

    def reverb(x, rt60=0.4, mix=0.2, predelay=0.012, bright=6000):
        n = int(SR * rt60)
        e = np.exp(-tt(rt60) * 6.9 / rt60)
        ir = lp(rng.standard_normal(n), bright, 1) * e
        ir[: int(predelay * SR)] = 0
        ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
        wet = signal.fftconvolve(x, ir)[: len(x) + n]
        out = np.zeros(len(wet))
        out[: len(x)] += x
        out += mix * wet * (np.max(np.abs(x)) / (np.max(np.abs(wet)) + 1e-9))
        return out

    def finish(name, buf, rms=0.16, fade=0.03):
        buf = np.asarray(buf, dtype=np.float64)
        buf = buf - np.mean(buf)
        r = np.sqrt(np.mean(buf ** 2)) + 1e-9
        buf = buf / r * rms
        buf = np.tanh(buf * 1.4) / 1.4          # limitador suave
        k = int(fade * SR)
        buf[-k:] *= np.linspace(1, 0, k)
        buf[:8] *= np.linspace(0, 1, 8)
        buf = buf / max(1.0, np.max(np.abs(buf)) / 0.92)
        sf.write(f'{PACK}/assets/shopkeepers/sounds/{name}.ogg', buf.astype('float32'), SR, format='OGG', subtype='VORBIS')

    # ---- monedas de metal: modos inarmonicos de un disco + tic de impacto
    COIN_RATIOS = (1.0, 1.46, 2.12, 2.80, 3.55, 4.6)
    COIN_AMPS = (1.0, 0.72, 0.5, 0.34, 0.22, 0.12)
    COIN_DEC = (8.0, 11.0, 15.0, 21.0, 30.0, 42.0)

    def coin(f0, d=0.55, vel=1.0):
        out = np.zeros(int(SR * d))
        for r, a, dec in zip(COIN_RATIOS, COIN_AMPS, COIN_DEC):
            out += modal(f0 * r * float(rng.uniform(0.995, 1.005)), d, dec, a, float(rng.uniform(0, 6.28)))
        tick = hp(noise(0.004), 3500) * np.exp(-tt(0.004) * 900)
        put(out, tick, 0, 1.4)
        return out * vel

    def thump(d=0.25, f=95.0):
        return modal(f, d, 22, 1.0) + modal(f * 1.55, d, 30, 0.5) + lp(noise(d), 260) * np.exp(-tt(d) * 25) * 1.2

    # ui/click: madera
    b = np.zeros(int(SR * 0.12))
    for r, a, dec in ((1.0, 1.0, 70), (2.76, 0.55, 110), (5.4, 0.25, 170)):
        put(b, modal(950 * r, 0.12, dec, a), 0)
    put(b, hp(noise(0.003), 2200) * np.exp(-tt(0.003) * 1200), 0, 0.8)
    finish('ui/click', lp(reverb(b, 0.12, 0.08), 7500), 0.15)

    # ui/page: pergamino que se arruga
    d = 0.4
    b = np.zeros(int(SR * d))
    for _ in range(46):
        pos = float(rng.beta(1.6, 2.2)) * (d - 0.02)
        g = bp(noise(0.004), 1800, 7000) * np.exp(-tt(0.004) * 700)
        put(b, g, pos, float(rng.uniform(0.3, 1.0)))
    swish = bp(noise(d), 900, 4200) * np.sin(np.pi * tt(d) / d) ** 2 * 0.25
    finish('ui/page', b + swish, 0.11)

    # ui/coin: dos monedas que chocan
    b = np.zeros(int(SR * 0.9))
    put(b, coin(2700, 0.8), 0.0, 1.0)
    put(b, coin(3300, 0.7), 0.075, 0.8)
    finish('ui/coin', reverb(b, 0.35, 0.18), 0.15)

    # ui/deposit: bolsa de cuero que cae y monedas que rebotan
    b = np.zeros(int(SR * 1.5))
    put(b, thump(), 0.0, 1.0)
    t_hit, gap = 0.07, 0.11
    for i in range(9):
        put(b, coin(float(rng.uniform(2300, 3600)), 0.55), t_hit, max(0.25, 1.0 - i * 0.09))
        t_hit += gap
        gap *= 0.82
    finish('ui/deposit', reverb(b, 0.45, 0.2), 0.16)

    # ui/withdraw: monedas deslizandose + cierre del cuero
    b = np.zeros(int(SR * 1.2))
    grains = np.zeros(int(SR * 0.45))
    for _ in range(70):
        pos = float(rng.uniform(0, 0.42))
        put(grains, bp(noise(0.006), 2500, 8000) * np.exp(-tt(0.006) * 500), pos, float(rng.uniform(0.2, 0.8)))
    put(b, grains, 0.0, 0.7)
    for i, f in enumerate((2500, 2900, 3300, 3700)):
        put(b, coin(f, 0.5), 0.2 + i * 0.07, 0.85)
    put(b, lp(noise(0.05), 900) * np.exp(-tt(0.05) * 60), 0.55, 0.9)
    finish('ui/withdraw', reverb(b, 0.4, 0.18), 0.15)

    # ui/error: golpe sordo de madera, dos veces
    b = np.zeros(int(SR * 0.6))
    for at, f in ((0.0, 150.0), (0.16, 112.0)):
        for r, a, dec in ((1.0, 1.0, 20), (1.8, 0.5, 30), (2.9, 0.25, 45)):
            put(b, modal(f * r, 0.4, dec, a), at)
        put(b, lp(noise(0.03), 1100) * np.exp(-tt(0.03) * 120), at, 0.8)
    finish('ui/error', reverb(b, 0.2, 0.12), 0.17)

    # trade/success: moneda + glockenspiel (barras de metal) en arpegio
    b = np.zeros(int(SR * 1.5))
    put(b, coin(3000, 0.6), 0.0, 0.9)
    for i, f in enumerate((1046.5, 1318.5, 1568.0, 2093.0)):
        for r, a, dec in ((1.0, 1.0, 4.5), (2.756, 0.45, 9.0), (5.404, 0.2, 18.0)):
            put(b, modal(f * r, 1.2, dec, a), 0.08 + i * 0.075, 0.55)
    finish('trade/success', reverb(b, 0.7, 0.28, bright=8000), 0.14)

    # event/horn: cuerno de bronce (3 voces, brillo que se abre, vibrato, sala grande)
    def brass(f, d, voices=(-3.0, 0.0, 3.0)):
        x = tt(d)
        out = np.zeros_like(x)
        for cents in voices:
            fv = f * 2 ** (cents / 1200.0)
            scoop = 1 - 0.035 * np.exp(-x * 22)
            vib = 1 + 0.0035 * np.sin(2 * np.pi * 5.1 * x) * np.clip((x - 0.25) * 4, 0, 1)
            phase = 2 * np.pi * np.cumsum(fv * scoop * vib) / SR
            v = np.zeros_like(x)
            for h in range(1, 14):
                v += (1.0 / h ** 0.75) * np.sin(h * phase)
            out += v
        dark, bright = lp(out, 900), lp(out, 4200)
        mixc = np.clip(x / 0.16, 0, 1) * (0.55 + 0.45 * np.clip((x - 0.1) / 0.3, 0, 1))
        sig = dark * (1 - mixc) + bright * mixc
        env = np.minimum(1.0, x / 0.07) * np.minimum(1.0, (d - x) / 0.16)
        return sig * env

    b = np.zeros(int(SR * 3.4))
    for f, at, d in ((196.0, 0.0, 0.62), (261.6, 0.55, 0.62), (329.6, 1.1, 0.55), (392.0, 1.6, 1.3)):
        put(b, brass(f, d), at, 1.0)
    finish('event/horn', reverb(b, 1.5, 0.32, 0.03, 5000), 0.15, 0.2)

    # event/bell: campana de iglesia
    b = np.zeros(int(SR * 4.0))
    base = 330.0
    parts = ((0.5, 0.6, 0.9), (1.0, 1.0, 1.2), (1.19, 0.7, 1.5), (1.5, 0.6, 1.8), (2.0, 0.5, 2.2),
             (2.5, 0.35, 2.8), (3.0, 0.25, 3.4), (4.0, 0.18, 4.2), (5.33, 0.12, 5.0))
    for r, a, dec in parts:
        put(b, modal(base * r, 4.0, dec, a), 0.0)
        put(b, modal(base * r + 0.9, 4.0, dec * 1.05, a * 0.5), 0.0)   # batido que da "brillo"
    put(b, lp(noise(0.03), 3200) * np.exp(-tt(0.03) * 110), 0.0, 0.9)
    finish('event/bell', reverb(b, 1.8, 0.25, 0.02, 6000), 0.14, 0.4)

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
