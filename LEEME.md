# Shopkeepers Medieval (ADDON para Shopkeepers, Minecraft 1.21.1)

Es un plugin APARTE. No modifica Shopkeepers: tu servidor lleva **dos jars**.

## 1) Que poner en el servidor (Spigot/Paper 1.21.1, Java 21)
1. **Shopkeepers 2.24.0** oficial (la ultima version que soporta 1.21.1). Descargala de
   Spigot / Hangar / GitHub Releases de Shopkeepers. NO uses el que compilaste del fork
   (2.28.x): ese es solo para 1.21.5+ y no arranca en 1.21.1.
2. **ShopkeepersMedieval-1.0.0.jar** (este addon, lo compilas abajo).
Los dos van en `plugins/`. Si el addon dice que falta Shopkeepers, es que no esta Shopkeepers.

## 2) Compilar el addon en GitHub
1. Crea un repo y sube el CONTENIDO de esta carpeta.
2. Pestana *Actions* -> **Build** -> espera -> descarga el artifact *ShopkeepersMedieval*.
   Dentro esta el jar. (Tarda ~1-2 min; solo baja la API de Spigot y la de Shopkeepers.)

## 3) Uso rapido
- Las monedas se leen solas de `plugins/Shopkeepers/config.yml` (esmeralda por defecto).
- **Precios dinamicos**: mira a un mercader y escribe `/mercadoadmin tienda marcar`.
  Sus precios actuales pasan a ser el precio BASE y desde ahi suben/bajan con la demanda.
  `/mercadoadmin tienda desmarcar` devuelve los precios base. `/mercadoadmin tienda lista`.
  Funciona en tiendas de admin y en tiendas de jugador de venta/compra.
  Si editas una tienda marcada, lo que dejes en el editor pasa a ser el nuevo precio base.
- **Mercado**: `/mercado` (categorias con fondo propio) -> click en un objeto = grafica de precios.
  Eventos: `/mercadoadmin evento iniciar feria|guerra|escasez-hierro|cosecha|hambruna|fiebre-del-oro`
  `/mercadoadmin evento parar todos`, `evento crear <mult> <min> <ITEMS|*> <nombre>`,
  `demanda <ITEM> <valor>`, `reiniciar`, `recargar`.
- **Banco**: `/banco` (menu), `/banco saldo|depositar|retirar|pagar`. Banquero NPC: mira a un
  mercader de admin (con 1 trade como minimo) y `/bancoadmin npc`.
- **Rutas**: mira al mercader, parate donde quieres que este y `/ruta agregar 09:30` (reloj de
  Minecraft, 06:00 = amanecer). A esa hora camina y se queda. `/ruta listar|quitar|limpiar|ir|estado`.
- Config de todo: `plugins/ShopkeepersMedieval/medieval.yml`.

## 4) Resourcepack (fondos, botones, sonidos, skins)
Archivo: `resourcepack/ShopkeepersMedieval.zip`  (sha1: `16ec654463cd167b800edc2f8258ce79af397fc4`)
1. Subelo a un hosting con link directo (o ponlo en tu launcher/server.properties).
2. En `medieval.yml`: `gui.usar-resourcepack: true`, `gui.resourcepack.url`, `gui.resourcepack.sha1`
   y `enviar-al-entrar: true`. Luego `/mercadoadmin recargar`.
Incluye:
- 9 fondos distintos: Mercado, Grafica, Banco y las 6 categorias (Forja, Granja y Taberna,
  Alquimia, Minas y Gemas, Construccion, Bazar).
- 13 botones medievales, ventana de comercio de aldeano en madera/pergamino.
- 9 sonidos propios (clic, pagina, moneda, deposito, retiro, error, compra, cuerno y campana de eventos).
- Skins por oficio y por bioma de aldeano. En el editor del mercader elige el oficio:
  Armorer/Weaponsmith/Toolsmith = herreros, Farmer = panadero/granjero, Cleric = alquimista,
  Librarian = escriba/banquero, Cartographer, Butcher, Fisherman, Fletcher, Leatherworker,
  Mason, Shepherd. OJO: las skins cambian a TODOS los aldeanos del servidor (es del cliente).
Sin resourcepack todo funciona igual con items y sonidos normales de Minecraft.
Para regenerar/editar el pack: `python3 resourcepack_src/build_rp.py` (necesita Pillow, numpy, soundfile).

## 5) Si algo falla
- Mira la consola al arrancar: busca "Shopkeepers Medieval activo". Si hay un error, copiamelo.
- Rutas: si tu version de Shopkeepers no permite mover mercaderes, se desactivan solas y lo
  demas sigue funcionando.
