"""Estilo oscuro-dorado para los menus (botones anchos dibujados dentro del fondo).

Los botones se dibujan en el fondo (glifo de fuente) y encima se colocan items invisibles
con el texto de ayuda. Este archivo es la unica fuente de verdad de las posiciones: tambien
genera gui/Layouts.java para que el plugin use exactamente las mismas posiciones.
"""
import os, math, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
FONT_PATH = os.path.join(HERE, 'fonts', 'Cinzel.ttf')
K = 4                       # escala del dibujo (1024 px de lado)
RND = random.Random(77)

GOLD = (212, 175, 55)
GOLD_L = (255, 232, 140)
GOLD_D = (120, 94, 26)
BG0 = (9, 8, 11)
BG1 = (20, 17, 22)
IVORY = (240, 232, 208)


def S(v):
    return int(round(v * K))


def font(px, bold=True):
    f = ImageFont.truetype(FONT_PATH, max(8, int(px * K)))
    try:
        f.set_variation_by_name('Bold' if bold else 'Regular')
    except Exception:
        pass
    return f


def mixc(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scale(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def new_canvas():
    return Image.new('RGBA', (256 * K, 256 * K), (0, 0, 0, 0))


def vgrad(w, h, top, bottom):
    img = Image.new('RGBA', (w, h))
    px = img.load()
    for y in range(h):
        c = mixc(top, bottom, y / max(1, h - 1)) + (255,)
        for x in range(w):
            px[x, y] = c
    return img


def rr(d, box, r, **kw):
    x0, y0, x1, y1 = box
    d.rounded_rectangle([S(x0), S(y0), S(x1) - 1, S(y1) - 1], radius=S(r), **kw)


def glow(canvas, box, color, alpha, blur):
    layer = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle([S(box[0]), S(box[1]), S(box[2]) - 1, S(box[3]) - 1], radius=S(2),
                                            fill=color + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(S(blur)))
    canvas.alpha_composite(layer)


def text_gold(canvas, text, cx, cy, px):
    """Titulo grande en degradado dorado con brillo."""
    f = font(px)
    mask = Image.new('L', canvas.size, 0)
    md = ImageDraw.Draw(mask)
    bbox = md.textbbox((0, 0), text, font=f)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    pos = (S(cx) - w // 2 - bbox[0], S(cy) - h // 2 - bbox[1])
    md.text(pos, text, font=f, fill=255)
    glow_layer = Image.new('RGBA', canvas.size, GOLD + (0,))
    glow_layer.putalpha(mask.filter(ImageFilter.GaussianBlur(S(1.2))).point(lambda v: int(v * 0.55)))
    canvas.alpha_composite(glow_layer)
    grad = vgrad(canvas.size[0], canvas.size[1], GOLD_L, (196, 142, 36))
    grad.putalpha(mask)
    canvas.alpha_composite(grad)


def text_fit(canvas, text, x0, x1, cy, max_px, color=IVORY, min_px=3.2):
    """Texto centrado entre x0 y x1 (1x), reduciendo el tamano si no cabe."""
    px = max_px
    d = ImageDraw.Draw(canvas)
    while True:
        f = font(px)
        bbox = d.textbbox((0, 0), text, font=f)
        w = bbox[2] - bbox[0]
        if w <= S(x1 - x0) or px <= min_px:
            break
        px -= 0.2
    h = bbox[3] - bbox[1]
    x = S((x0 + x1) / 2) - w // 2 - bbox[0]
    y = S(cy) - h // 2 - bbox[1]
    d.text((x + S(0.35), y + S(0.35)), text, font=f, fill=(0, 0, 0, 200))
    d.text((x, y), text, font=f, fill=color + (255,))


def slot_xy(col, row):
    return 7 + 18 * col, 17 + 18 * row


def dark_panel(canvas, accent=GOLD):
    """Marco oscuro con doble linea dorada y esquinas; incluye el inventario del jugador."""
    d = ImageDraw.Draw(canvas)
    panel = vgrad(S(176), S(222), BG1, BG0)
    canvas.alpha_composite(panel, (0, 0))
    d = ImageDraw.Draw(canvas)
    rr(d, (0, 0, 176, 222), 1.5, outline=GOLD_D + (255,), width=S(1))
    rr(d, (1.5, 1.5, 174.5, 220.5), 1.2, outline=GOLD + (255,), width=S(0.8))
    rr(d, (3, 3, 173, 219), 1.0, outline=scale(GOLD_D, 0.7) + (255,), width=S(0.5))
    # esquinas
    for (x, y) in ((4.5, 4.5), (171.5, 4.5), (4.5, 217.5), (171.5, 217.5)):
        d.ellipse([S(x - 1.4), S(y - 1.4), S(x + 1.4), S(y + 1.4)], fill=GOLD_L + (255,), outline=GOLD_D + (255,))
    # separador entre el menu y el inventario
    d.line([S(8), S(136.5), S(168), S(136.5)], fill=GOLD_D + (255,), width=S(0.7))
    cx = 88
    d.polygon([(S(cx), S(134.8)), (S(cx + 2.2), S(136.5)), (S(cx), S(138.2)), (S(cx - 2.2), S(136.5))],
              fill=GOLD + (255,))
    # inventario del jugador: celdas negras con lineas doradas tenues
    for r in range(3):
        for c in range(9):
            x, y = 7 + 18 * c, 139 + 18 * r
            d.rectangle([S(x), S(y), S(x + 18) - 1, S(y + 18) - 1], fill=(5, 5, 7, 255),
                        outline=scale(GOLD_D, 0.62) + (255,), width=max(1, S(0.35)))
    for c in range(9):
        x, y = 7 + 18 * c, 197
        d.rectangle([S(x), S(y), S(x + 18) - 1, S(y + 18) - 1], fill=(5, 5, 7, 255),
                    outline=scale(GOLD_D, 0.62) + (255,), width=max(1, S(0.35)))


def header(canvas, title=None):
    """Titulo centrado con lineas doradas a los lados (como el estilo de referencia)."""
    d = ImageDraw.Draw(canvas)
    half = 0.0
    if title:
        f = font(10.5)
        bbox = d.textbbox((0, 0), title, font=f)
        half = (bbox[2] - bbox[0]) / K / 2.0 + 4.0
    left_end = 88 - half if title else 46
    right_start = 88 + half if title else 130
    for (x0, x1) in ((9, min(left_end, 80)), (max(right_start, 96), 167)):
        if x1 - x0 < 4:
            continue
        d.line([S(x0), S(8.6), S(x1), S(8.6)], fill=GOLD + (255,), width=S(0.5))
        for x in (x0, x1):
            d.ellipse([S(x - 0.8), S(7.8), S(x + 0.8), S(9.4)], fill=GOLD_L + (255,))
    if title:
        text_gold(canvas, title, 88, 8.8, 10.5)


def info_slot(canvas, col=4, row=0):
    """Cajita oscura para el item de informacion (como el banner del ejemplo)."""
    x, y = slot_xy(col, row)
    d = ImageDraw.Draw(canvas)
    rr(d, (x, y, x + 18, y + 18), 1.0, fill=(4, 4, 6, 255), outline=GOLD + (230,), width=S(0.45))


def wide_button(canvas, col, row, width, label, accent, label_cols_skip=1):
    """Boton ancho: espacio para el icono a la izquierda (item real) y el texto a la derecha."""
    x, y = slot_xy(col, row)
    x0, y0, x1, y1 = x + 0.6, y + 0.8, x + 18 * width - 0.6, y + 17.2
    glow(canvas, (x0 - 0.5, y0 - 0.5, x1 + 0.5, y1 + 0.5), accent, 90, 1.6)
    # relleno con degradado horizontal (de mas claro a la izquierda, a oscuro a la derecha)
    w, h = S(x1 - x0), S(y1 - y0)
    base = Image.new('RGBA', (w, h))
    px = base.load()
    left = mixc(scale(accent, 0.62), (14, 12, 16), 0.15)
    right = mixc(scale(accent, 0.22), (8, 8, 10), 0.35)
    for xx in range(w):
        t = xx / max(1, w - 1)
        c = mixc(left, right, min(1.0, t * 1.15))
        for yy in range(h):
            v = 1.0 + 0.16 * (1.0 - yy / max(1, h - 1)) - 0.10 * (yy / max(1, h - 1))
            px[xx, yy] = scale(c, v) + (255,)
    mask = Image.new('L', (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=S(1.3), fill=255)
    base.putalpha(mask)
    canvas.alpha_composite(base, (S(x0), S(y0)))
    d = ImageDraw.Draw(canvas)
    # halo detras del icono
    hx, hy = x + 9, y + 9
    halo = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse([S(hx - 7.5), S(hy - 7.5), S(hx + 7.5), S(hy + 7.5)], fill=mixc(accent, (255, 255, 255), 0.25) + (110,))
    canvas.alpha_composite(halo.filter(ImageFilter.GaussianBlur(S(2.2))))
    # borde
    edge = mixc(accent, GOLD, 0.55)
    rr(d, (x0, y0, x1, y1), 1.3, outline=edge + (235,), width=S(0.45))
    d.line([S(x0 + 1.5), S(y0 + 0.6), S(x1 - 1.5), S(y0 + 0.6)], fill=mixc(accent, (255, 255, 255), 0.5) + (110,), width=max(1, S(0.25)))
    for (cx, cy) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        d.ellipse([S(cx - 0.7), S(cy - 0.7), S(cx + 0.7), S(cy + 0.7)], fill=mixc(accent, (255, 255, 255), 0.55) + (255,))
    # texto en la zona derecha
    tx0 = x + 18 * label_cols_skip + 1.0
    tx1 = x1 - 2.0
    if '\n' in label:
        top, bottom = label.split('\n', 1)
        text_fit(canvas, top, tx0, tx1, y + 5.2, 4.6, color=mixc(IVORY, GOLD, 0.45))
        text_fit(canvas, bottom, tx0, tx1, y + 12.0, 7.4)
    else:
        text_fit(canvas, label, tx0, tx1, y + 9.2, 7.2)


def chest_cells(canvas, rows, accent):
    d = ImageDraw.Draw(canvas)
    for r in range(rows):
        for c in range(9):
            x, y = slot_xy(c, r)
            d.rectangle([S(x), S(y), S(x + 18) - 1, S(y + 18) - 1], fill=(8, 7, 10, 255),
                        outline=mixc(scale(accent, 0.5), GOLD_D, 0.4) + (255,), width=max(1, S(0.35)))


def emblem(canvas, cx, cy, r, accent):
    """Moneda grande y tenue, para rellenar zonas vacias."""
    layer = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([S(cx - r), S(cy - r), S(cx + r), S(cy + r)], outline=GOLD + (70,), width=S(0.8))
    d.ellipse([S(cx - r * 0.78), S(cy - r * 0.78), S(cx + r * 0.78), S(cy + r * 0.78)], outline=GOLD + (45,), width=S(0.5))
    d.polygon([(S(cx), S(cy - r * 0.45)), (S(cx + r * 0.32), S(cy)), (S(cx), S(cy + r * 0.45)), (S(cx - r * 0.32), S(cy))],
              fill=GOLD + (50,))
    canvas.alpha_composite(layer)


# ---------------------------------------------------------------- Disposicion de menus
C = {  # colores de acento
    'rojo': (190, 55, 50), 'naranja': (205, 120, 35), 'dorado': (205, 155, 30), 'verde': (55, 150, 80),
    'verde2': (60, 150, 60), 'morado': (140, 65, 185), 'azul': (50, 100, 185), 'celeste': (45, 140, 195),
    'turquesa': (40, 145, 150), 'gris': (115, 115, 128), 'marron': (170, 110, 50), 'rojo2': (175, 45, 60),
}


def B(id, row, col, width, label, accent):
    return dict(id=id, row=row, col=col, width=width, label=label, accent=C[accent])


LAYOUTS = {
    'HUB': dict(bg='bg_hub', title='MERCADO', info=True, emblem=True, buttons=[
        B('forja', 1, 0, 3, 'LA FORJA', 'rojo'), B('granja', 1, 3, 3, 'GRANJA', 'verde2'), B('alquimia', 1, 6, 3, 'ALQUIMIA', 'morado'),
        B('minas', 2, 0, 3, 'MINAS', 'celeste'), B('construccion', 2, 3, 3, 'CONSTRUCCIÓN', 'marron'), B('bazar', 2, 6, 3, 'BAZAR', 'dorado'),
        B('todos', 3, 0, 3, 'TODOS', 'turquesa'), B('eventos', 3, 3, 3, 'EVENTOS', 'rojo2'), B('banco', 3, 6, 3, 'BANCO', 'dorado'),
        B('ayuda', 4, 0, 4, 'AYUDA', 'azul'), B('cerrar', 4, 5, 4, 'CERRAR', 'gris'),
    ]),
    'BANK': dict(bg='bg_bank_menu', title='BANCO', info=True, emblem=True, emblem_y=89, emblem_r=15, buttons=[
        B('dep1', 1, 0, 3, 'DEPOSITAR\n1', 'verde'), B('dep10', 1, 3, 3, 'DEPOSITAR\n10', 'verde'), B('depall', 1, 6, 3, 'DEPOSITAR\nTODO', 'verde'),
        B('ret1', 2, 0, 3, 'RETIRAR\n1', 'rojo2'), B('ret10', 2, 3, 3, 'RETIRAR\n10', 'rojo2'), B('retall', 2, 6, 3, 'RETIRAR\nTODO', 'rojo2'),
        B('mercado', 5, 0, 4, 'MERCADO', 'turquesa'), B('cerrar', 5, 5, 4, 'CERRAR', 'gris'),
    ]),
    'BANK_HIGH': dict(bg='bg_bank_high', title='BANCO', info=True, buttons=[
        B('dep1', 1, 0, 3, 'DEPOSITAR\n1', 'verde'), B('dep10', 1, 3, 3, 'DEPOSITAR\n10', 'verde'), B('depall', 1, 6, 3, 'DEPOSITAR\nTODO', 'verde'),
        B('ret1', 2, 0, 3, 'RETIRAR\n1', 'rojo2'), B('ret10', 2, 3, 3, 'RETIRAR\n10', 'rojo2'), B('retall', 2, 6, 3, 'RETIRAR\nTODO', 'rojo2'),
        B('hdep1', 3, 0, 3, 'DEPOSITAR\n1', 'verde'), B('hdep10', 3, 3, 3, 'DEPOSITAR\n10', 'verde'), B('hdepall', 3, 6, 3, 'DEPOSITAR\nTODO', 'verde'),
        B('hret1', 4, 0, 3, 'RETIRAR\n1', 'rojo2'), B('hret10', 4, 3, 3, 'RETIRAR\n10', 'rojo2'), B('hretall', 4, 6, 3, 'RETIRAR\nTODO', 'rojo2'),
        B('mercado', 5, 0, 4, 'MERCADO', 'turquesa'), B('cerrar', 5, 5, 4, 'CERRAR', 'gris'),
    ]),
    'CONFIG': dict(bg='bg_config_menu', title='MERCADER', info=True, buttons=[
        B('dinamicos', 1, 0, 3, 'DINÁMICOS', 'verde'), B('stock', 1, 3, 3, 'STOCK', 'naranja'), B('banquero', 1, 6, 3, 'BANQUERO', 'dorado'),
        B('moneda', 2, 0, 3, 'MONEDA', 'dorado'), B('alta', 2, 3, 3, 'MONEDA ALTA', 'marron'), B('valor', 2, 6, 3, 'VALOR ALTA', 'naranja'),
        B('reset', 3, 0, 3, 'GLOBAL', 'rojo2'), B('actualizar', 3, 3, 3, 'ACTUALIZAR', 'turquesa'), B('abrirbanco', 3, 6, 3, 'BANCO', 'dorado'),
        B('cerrar', 5, 3, 3, 'CERRAR', 'gris'),
    ]),
}


def render_layout(name, spec):
    canvas = new_canvas()
    dark_panel(canvas)
    header(canvas, spec.get('title'))
    if spec.get('info'):
        info_slot(canvas)
    if spec.get('emblem'):
        emblem(canvas, 88, spec.get('emblem_y', 116), spec.get('emblem_r', 9), GOLD)
    for b in spec['buttons']:
        wide_button(canvas, b['col'], b['row'], b['width'], b['label'], tuple(b['accent']))
    return canvas


def render_list(accent, title_line=True):
    """Fondo para menus con items (lista de precios, grafica): celdas oscuras + barra inferior."""
    canvas = new_canvas()
    dark_panel(canvas)
    d = ImageDraw.Draw(canvas)
    d.line([S(8), S(15.5), S(168), S(15.5)], fill=mixc(accent, GOLD, 0.5) + (255,), width=S(0.6))
    chest_cells(canvas, 5, accent)
    # barra inferior para los botones de un solo slot
    x, y = slot_xy(0, 5)
    rr(d, (x, y + 0.5, x + 162, y + 17.5), 1.5, fill=(15, 12, 17, 255), outline=mixc(scale(accent, 0.7), GOLD_D, 0.4) + (255,), width=S(0.5))
    for c in range(9):
        cx, cy = slot_xy(c, 5)
        rr(d, (cx + 1, cy + 1, cx + 17, cy + 17), 1.4, fill=(6, 6, 9, 255), outline=scale(GOLD_D, 0.8) + (255,), width=max(1, S(0.3)))
    return canvas


def java_source(chars):
    lines = ['package me.medieval.shops.gui;', '',
             '/** GENERADO por resourcepack_src/gui_dark.py: posiciones de los botones dibujados en los fondos. */',
             'public final class Layouts {', '',
             '\tpublic static final class Spec {',
             '\t\tpublic final String id;', '\t\tpublic final int row;', '\t\tpublic final int col;', '\t\tpublic final int width;', '',
             '\t\tSpec(String id, int row, int col, int width) {',
             '\t\t\tthis.id = id;', '\t\t\tthis.row = row;', '\t\t\tthis.col = col;', '\t\t\tthis.width = width;', '\t\t}', '\t}', '',
             '\tpublic static final class Layout {',
             '\t\tpublic final char bg;', '\t\tpublic final Spec[] buttons;', '',
             '\t\tLayout(char bg, Spec... buttons) {', '\t\t\tthis.bg = bg;', '\t\t\tthis.buttons = buttons;', '\t\t}', '',
             '\t\tpublic Spec find(String id) {',
             '\t\t\tfor (Spec spec : buttons) {', '\t\t\t\tif (spec.id.equals(id)) return spec;', '\t\t\t}',
             '\t\t\treturn null;', '\t\t}', '\t}', '']
    for key, spec in LAYOUTS.items():
        btns = ',\n\t\t\t\t'.join(f'new Spec("{b["id"]}", {b["row"]}, {b["col"]}, {b["width"]})' for b in spec['buttons'])
        lines.append(f"\tpublic static final Layout {key} = new Layout('\\u{ord(chars[spec['bg']]):04X}',\n\t\t\t\t{btns});")
        lines.append('')
    lines.append('\tprivate Layouts() {')
    lines.append('\t}')
    lines.append('}')
    return '\n'.join(lines) + '\n'
