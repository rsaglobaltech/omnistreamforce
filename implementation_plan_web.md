# OmniStreamForce — Plan de Diseño de la Interfaz Web

> Documento maestro de diseño visual, UX y arquitectura frontend para la plataforma OmniStreamForce.
> Todas las features existentes del CLI, el motor de generación, la capa de persistencia (Outbox/Relay) y la integración con IA quedan representadas en la interfaz.

---

## 1. Filosofía de Diseño

### 1.1 Principios Rectores

| Principio | Descripción |
|---|---|
| **Command Center** | La interfaz debe sentirse como un centro de control de misión: datos en tiempo real, gráficos vivos, y control total sin salir de la pantalla. |
| **Dark-First Premium** | Modo oscuro como base. Colores vibrantes sobre fondos profundos. Glassmorphism sutil en paneles flotantes. |
| **Zero-Friction Flow** | Wizard guiado de 4 pasos para ir de "cero" a "publicando eventos" en menos de 2 minutos. |
| **Information Density** | Mostrar datos densos sin abrumar. Uso de progressive disclosure: resúmenes visibles, detalles bajo interacción. |
| **Alive & Responsive** | Micro-animaciones en cada interacción. Los gráficos respiran. Los contadores cuentan. Nada es estático. |

### 1.2 Inspiraciones Visuales

- **Vercel Dashboard** — Elegancia minimalista, tipografía limpia, dark mode premium.
- **Linear App** — Fluidez de transiciones, keyboard-first, diseño de ingeniería.
- **Grafana** — Densidad de datos en dashboards, gráficos en tiempo real.
- **Railway.app** — Topología visual de servicios, colores vibrantes sobre oscuro.
- **Stripe Dashboard** — Claridad en métricas financieras, micro-animaciones sutiles.

---

## 2. Sistema de Diseño (Design Tokens)

### 2.1 Paleta de Colores

```
┌─────────────────────────────────────────────────────────────────┐
│  DARK FOUNDATION                                                │
│  ─────────────────                                              │
│  --surface-0:    #0A0A0F    (fondo principal, casi negro azulado)│
│  --surface-1:    #12121A    (paneles, cards)                     │
│  --surface-2:    #1A1A28    (elementos elevados, modales)        │
│  --surface-3:    #242436    (hover states, bordes activos)       │
│  --border:       #2A2A3C    (bordes sutiles)                     │
│  --border-hover: #3A3A52    (bordes en hover)                    │
│                                                                  │
│  TEXT HIERARCHY                                                  │
│  ──────────────                                                  │
│  --text-primary:   #F0F0F5  (títulos, datos clave)               │
│  --text-secondary: #8B8BA3  (labels, descripciones)              │
│  --text-muted:     #55556A  (hints, placeholders)                │
│                                                                  │
│  ACCENT SPECTRUM (saturados y vibrantes)                         │
│  ───────────────                                                 │
│  --accent-cyan:     #00D4FF  (primario, acciones, links)         │
│  --accent-violet:   #8B5CF6  (AI features, esquemas)             │
│  --accent-emerald:  #10B981  (éxito, eventos normales)           │
│  --accent-amber:    #F59E0B  (warnings, alertas)                 │
│  --accent-rose:     #F43F5E  (errores, fallos, danger)           │
│  --accent-blue:     #3B82F6  (informativo, links secundarios)    │
│  --accent-orange:   #F97316  (outbox/relay, persistencia)        │
│                                                                  │
│  GRADIENTS (para fondos de hero, cards destacadas, gráficos)     │
│  ─────────                                                       │
│  --gradient-hero:   linear-gradient(135deg, #0A0A0F, #1A1030)    │
│  --gradient-cyan:   linear-gradient(135deg, #00D4FF, #0091D5)    │
│  --gradient-violet: linear-gradient(135deg, #8B5CF6, #6D28D9)    │
│  --gradient-glow:   radial-gradient(ellipse at 50% 0%,           │
│                       rgba(0,212,255,0.15) 0%, transparent 70%)  │
│                                                                  │
│  DOMAIN COLORS (cada dominio tiene un color exclusivo)           │
│  ─────────────                                                   │
│  Healthcare:   #10B981 (emerald)                                 │
│  Ecommerce:    #3B82F6 (blue)                                    │
│  FastFood:     #F97316 (orange)                                  │
│  Energy:       #EAB308 (yellow)                                  │
│  Autos:        #EC4899 (pink)                                    │
│  Highway:      #6366F1 (indigo)                                  │
│  Custom (AI):  #8B5CF6 (violet)                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Tipografía

| Uso | Fuente | Peso | Tamaño | Tracking |
|---|---|---|---|---|
| Display / Hero | **Inter** | 700 (Bold) | 48px / 36px | -0.02em |
| Heading H1 | Inter | 600 (SemiBold) | 28px | -0.01em |
| Heading H2 | Inter | 600 | 22px | -0.01em |
| Heading H3 | Inter | 500 (Medium) | 18px | 0 |
| Body | Inter | 400 (Regular) | 14px | 0 |
| Small / Labels | Inter | 500 | 12px | 0.02em |
| Monospace (datos, código, IDs) | **JetBrains Mono** | 400 | 13px | 0 |
| Métricas grandes (contadores) | JetBrains Mono | 700 | 32px | -0.02em |

### 2.3 Espaciado y Grid

```
--space-xs:   4px
--space-sm:   8px
--space-md:   16px
--space-lg:   24px
--space-xl:   32px
--space-2xl:  48px
--space-3xl:  64px

Grid principal:   12 columnas, gap 24px, max-width 1440px
Sidebar:          260px fija colapsable a 64px (solo iconos)
Content area:     1fr (fluida)
Panel derecho:    380px (detalles contextuales, colapsable)
```

### 2.4 Elevación y Profundidad

| Nivel | Uso | Sombra | Border |
|---|---|---|---|
| 0 | Fondo base | ninguna | ninguno |
| 1 | Cards, paneles | `0 1px 3px rgba(0,0,0,0.3)` | `1px solid var(--border)` |
| 2 | Dropdowns, popovers | `0 4px 12px rgba(0,0,0,0.4)` | `1px solid var(--border-hover)` |
| 3 | Modales, toasts | `0 8px 32px rgba(0,0,0,0.5)` | `1px solid var(--border-hover)` |
| Glass | Paneles flotantes dashboard | `backdrop-filter: blur(12px); background: rgba(18,18,26,0.75)` | `1px solid rgba(255,255,255,0.06)` |

### 2.5 Border Radius

```
--radius-sm:   6px     (botones pequeños, inputs)
--radius-md:   10px    (cards, paneles)
--radius-lg:   16px    (modales, hero sections)
--radius-full: 9999px  (badges, pills, avatares)
```

### 2.6 Iconografía

- **Librería**: Lucide Icons (trazo fino, estilo lineal, coherente con el tono premium).
- **Tamaños**: 16px (inline), 20px (botones), 24px (sidebar nav), 32px (feature icons).
- Los iconos de dominio usan versiones **filled** con el color del dominio como fill.

---

## 3. Animaciones y Micro-Interacciones

### 3.1 Transiciones Base

```css
/* Curva de easing personalizada: "snappy but smooth" */
--ease-out-expo: cubic-bezier(0.16, 1, 0.3, 1);
--ease-in-out:   cubic-bezier(0.4, 0, 0.2, 1);

/* Duraciones */
--duration-fast:    120ms   /* hovers, toggles */
--duration-normal:  250ms   /* transiciones de paneles */
--duration-slow:    400ms   /* modales, cambios de vista */
--duration-data:    800ms   /* animaciones de gráficos */
```

### 3.2 Catálogo de Micro-Interacciones

| Interacción | Animación | Detalles |
|---|---|---|
| **Hover sobre Card** | Elevación + glow | La card sube 2px y un brillo sutil del color del dominio aparece en el borde superior. |
| **Click en botón primario** | Scale down → up | 0.97 → 1.0, con ripple radial desde el punto de click. |
| **Toggle ON/OFF** | Slide + color morph | El thumb del switch desliza con easing exponencial; el track cambia de gris a cyan. |
| **Contadores en vivo** | Odómetro / Flip | Los dígitos de las métricas rotan verticalmente al cambiar, como un odómetro mecánico. |
| **Gráfico EPS** | Line draw | La línea del gráfico se dibuja con `stroke-dashoffset` animado. Los puntos nuevos entran con un fade-in + scale. |
| **Añadir dominio** | Card slide-in | La card del nuevo dominio entra desde la derecha con un spring animation (`transform: translateX(100%) → 0`). |
| **Eliminar dominio** | Card collapse | La card se comprime verticalmente (height → 0) con fade-out simultáneo. |
| **Notificación/Toast** | Slide down + auto-dismiss | Entra desde arriba, permanece 4s, sale con fade-up. Barra de progreso en la base del toast. |
| **Wizard step transition** | Crossfade + slide | El paso actual sale con fade-left, el nuevo entra con fade-right. Barra de progreso top se llena suavemente. |
| **Pulso de estado** | Glow pulse | Un punto verde/rojo junto al estado del engine pulsa suavemente (`box-shadow` con keyframes). |
| **Conexión exitosa** | Checkmark draw | Un SVG de checkmark se dibuja con `stroke-dasharray` animation, seguido de un breve confetti verde. |
| **Error de conexión** | Shake + flash rojo | El formulario hace un shake horizontal (3 ciclos, 4px) con un flash rojo en el borde. |
| **Slider (EPS, Error Rate)** | Valor flotante** | Un tooltip flotante sigue al thumb del slider mostrando el valor actual en tiempo real. El track se llena con un gradiente que va de verde a rojo según el valor. |

### 3.3 Efecto "Glow Ambiental"

En la vista del Dashboard en vivo, el fondo de la página tiene un **gradiente radial sutil** que cambia de tono según el estado del sistema:
- **Generando (activo)**: Glow cyan sutil en la esquina superior derecha.
- **Pausado**: Glow ámbar difuso.
- **Error / Circuit Breaker abierto**: Glow rojo suave pulsante.
- **Idle**: Sin glow, fondo neutro.

Esto se logra con un `div` absoluto con `radial-gradient` y `opacity` animada. No interfiere con la lectura pero da una sensación visceral del estado del sistema.

---

## 4. Arquitectura de Navegación

```
┌──────────────────────────────────────────────────────────────────────┐
│ ┌──────────┐                                                         │
│ │          │  SIDEBAR (260px, colapsable a 64px)                     │
│ │   LOGO   │  ─────────────────────────────────────                  │
│ │          │                                                         │
│ ├──────────┤  🏠  Dashboard           ← Centro de control en vivo   │
│ │          │  ⚡  Streams             ← Constructor de dominios      │
│ │  NAV     │  🔌  Conexiones          ← Kafka & DB connections       │
│ │  ITEMS   │  🧠  IA Studio           ← Editor de esquemas con IA   │
│ │          │  📦  Outbox & Relay      ← Pipeline de persistencia     │
│ │          │  📊  Métricas            ← Métricas históricas          │
│ │          │  ⚙️  Configuración       ← Perfiles, YAML, settings     │
│ │          │                                                         │
│ ├──────────┤  ─────────────────────────────────────                  │
│ │ FOOTER   │  Estado: ● Conectado a MSK us-east-1                    │
│ │          │  Engine: ▶ 3 dominios activos                           │
│ └──────────┘                                                         │
│                                                                      │
│ ┌────────────────────────────────────────────────────────────────┐   │
│ │  HEADER BAR (fija, 56px)                                       │   │
│ │  ┌──────────────────────┐  ┌─────┐ ┌─────┐ ┌──────────────┐  │   │
│ │  │ 🔍 Command Palette   │  │ 🔔  │ │ ❓  │ │ RS  (avatar) │  │   │
│ │  │    Ctrl+K             │  │     │ │     │ │              │  │   │
│ │  └──────────────────────┘  └─────┘ └─────┘ └──────────────┘  │   │
│ └────────────────────────────────────────────────────────────────┘   │
│                                                                      │
│ ┌────────────────────────────────────────────────────────────────┐   │
│ │                                                                │   │
│ │                     CONTENT AREA                               │   │
│ │                     (scrollable, 1fr)                          │   │
│ │                                                                │   │
│ └────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.1 Command Palette (Ctrl+K)

Barra de búsqueda/acciones rápidas estilo Spotlight/Linear:
- **Buscar dominio**: "healthcare" → ir a configuración del dominio.
- **Acciones rápidas**: "Pausar todo", "Cambiar EPS a 500", "Conectar a MSK".
- **Navegación**: "Ir a Outbox", "Ir a Dashboard".
- Fondo: `backdrop-filter: blur(20px)`, overlay semi-transparente.

---

## 5. Vistas Principales (Diseño Detallado)

---

### 5.1 Vista: Landing / Wizard de Inicio Rápido

**Ruta**: `/` (primera vez) o `/setup`

**Propósito**: Guiar al usuario desde cero hasta publicar eventos en 4 pasos.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                         │
│   ┌─────────────────────── PROGRESS BAR ──────────────────────────┐    │
│   │  ●━━━━━━━● ─ ─ ─ ─ ○ ─ ─ ─ ─ ○ ─ ─ ─ ─ ○                   │    │
│   │  Conexión    Dominios    Esquemas    Lanzar                   │    │
│   └───────────────────────────────────────────────────────────────┘    │
│                                                                         │
│   ┌───────────────────────────────────────────────────────────────┐    │
│   │                                                               │    │
│   │   PASO 1: Conecta tu Cluster                                 │    │
│   │   ─────────────────────────────                               │    │
│   │                                                               │    │
│   │   Selecciona tu entorno:                                      │    │
│   │                                                               │    │
│   │   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │    │
│   │   │   🖥️         │  │   ☁️  AWS    │  │   ☁️  CONF   │         │    │
│   │   │             │  │             │  │             │         │    │
│   │   │   Local /   │  │   AWS MSK   │  │  Confluent  │         │    │
│   │   │ Self-hosted │  │             │  │   Cloud     │         │    │
│   │   │             │  │  IAM/SCRAM  │  │             │         │    │
│   │   └─────────────┘  └─────────────┘  └─────────────┘         │    │
│   │         ↑ selected (cyan border + glow)                       │    │
│   │                                                               │    │
│   │   Bootstrap Servers                                           │    │
│   │   ┌─────────────────────────────────────────────────────┐    │    │
│   │   │  localhost:9092                                       │    │    │
│   │   └─────────────────────────────────────────────────────┘    │    │
│   │                                                               │    │
│   │   Security Protocol                                           │    │
│   │   ┌─────────────────────┐                                    │    │
│   │   │  PLAINTEXT        ▼ │                                    │    │
│   │   └─────────────────────┘                                    │    │
│   │                                                               │    │
│   │   ┌──────────────────────────────────┐                       │    │
│   │   │  🔌  Probar Conexión              │ ← botón primario     │    │
│   │   └──────────────────────────────────┘   cyan, full-width    │    │
│   │                                                               │    │
│   │   ─ ─ ─ ─ ─ ─ Resultado ─ ─ ─ ─ ─ ─                        │    │
│   │   ┌──────────────────────────────────────────────────────┐   │    │
│   │   │  ✅  Conectado                                        │   │    │
│   │   │  Cluster: kafka-local (3 brokers, KRaft, v3.8.0)    │   │    │
│   │   │  Topics: 12 encontrados                               │   │    │
│   │   └──────────────────────────────────────────────────────┘   │    │
│   │                                                               │    │
│   │            ┌──────────────┐  ┌───────────────────┐           │    │
│   │            │   ← Atrás    │  │  Siguiente →       │           │    │
│   │            └──────────────┘  └───────────────────┘           │    │
│   └───────────────────────────────────────────────────────────────┘    │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Detalles visuales del Paso 1**:
- Las 3 tarjetas de cluster type tienen un **icono animado** al hacer hover (el icono de nube se eleva ligeramente).
- La tarjeta seleccionada tiene un **borde cyan brillante** con `box-shadow: 0 0 20px rgba(0,212,255,0.2)`.
- Al seleccionar "AWS MSK", aparecen campos adicionales (Region, Auth Type) con una **animación de expansión** suave.
- El botón "Probar Conexión" muestra un **spinner animado** durante la prueba, y al tener éxito, el spinner se transforma en un **checkmark animado** (SVG path draw).
- Si falla, el campo de bootstrap servers hace un **shake** y el borde se pone rojo momentáneamente.

---

### 5.2 Vista: Constructor de Streams (Dominios & Topic Mapping)

**Ruta**: `/streams`

**Propósito**: Configurar qué dominios publicar, a qué topics, con qué velocidad y tasa de errores. Incluye selección de sink (Kafka / DB Outbox / Dual).

```
┌──────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│  Streams Activos                                          [+ Añadir]    │
│  ════════════════                                                        │
│                                                                          │
│  ┌─ DOMAIN CARDS (horizontal scroll si > 4) ─────────────────────────┐  │
│  │                                                                    │  │
│  │  ┌────────────────────┐  ┌────────────────────┐  ┌──────────────┐│  │
│  │  │ 🟢 ● Healthcare    │  │ 🔵 ● Ecommerce     │  │ 🟠 ● FastFood││  │
│  │  │                    │  │                    │  │              ││  │
│  │  │  ▶ ACTIVO          │  │  ▶ ACTIVO          │  │  ⏸ PAUSADO   ││  │
│  │  │                    │  │                    │  │              ││  │
│  │  │  Topic:            │  │  Topic:            │  │  Topic:      ││  │
│  │  │  healthcare-events │  │  ecommerce-orders  │  │  fastfood-ev ││  │
│  │  │                    │  │                    │  │              ││  │
│  │  │  EPS: 50     ⚡    │  │  EPS: 100    ⚡    │  │  EPS: 200  ⚡ ││  │
│  │  │  Errors: 10%  ⚠    │  │  Errors: 15%  ⚠   │  │  Errors: 5% ⚠││  │
│  │  │                    │  │                    │  │              ││  │
│  │  │  Sink: KAFKA       │  │  Sink: DUAL        │  │  Sink: OUTBOX││  │
│  │  │                    │  │                    │  │              ││  │
│  │  │  [⏸][⏹][⚙️]       │  │  [⏸][⏹][⚙️]       │  │  [▶][⏹][⚙️] ││  │
│  │  └────────────────────┘  └────────────────────┘  └──────────────┘│  │
│  │                                                                    │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ═══════════════════════════════════════════════════════════════════     │
│                                                                          │
│  Detalle del Stream Seleccionado: Healthcare                             │
│  ────────────────────────────────────────────                            │
│                                                                          │
│  ┌─ CONFIGURACIÓN ──────────┐  ┌─ TOPIC MAPPING ────────────────────┐  │
│  │                           │  │                                     │  │
│  │  Dominio:  Healthcare     │  │  Target Topic:                      │  │
│  │                           │  │  ┌─────────────────────────┐       │  │
│  │  Eventos/s (EPS)          │  │  │ healthcare-events     ▼ │       │  │
│  │  ○━━━━━━━━━━━●━━━━━━━━━○ │  │  └─────────────────────────┘       │  │
│  │  1          50        500 │  │                                     │  │
│  │              ↑ valor       │  │  Error Topic:                       │  │
│  │             [50]          │  │  ┌─────────────────────────┐       │  │
│  │                           │  │  │ healthcare-errors     ▼ │       │  │
│  │  Tasa de Errores (%)      │  │  └─────────────────────────┘       │  │
│  │  ○━━━━━●━━━━━━━━━━━━━━━○ │  │                                     │  │
│  │  0%   10%             100%│  │  Key Strategy:                      │  │
│  │        ↑                  │  │  ◉ Entity ID (patientId)            │  │
│  │       [10%]               │  │  ○ Random UUID                      │  │
│  │                           │  │  ○ Round Robin                      │  │
│  │  Modo de Publicación      │  │                                     │  │
│  │  ┌──────┬──────┬────────┐│  │  Topics de Error Separados: ● ON    │  │
│  │  │STEADY│BURST │ SPIKE  ││  │                                     │  │
│  │  │  ●   │      │        ││  │  Formato: JSON ▼                    │  │
│  │  └──────┴──────┴────────┘│  │                                     │  │
│  │                           │  └─────────────────────────────────────┘  │
│  │  Sink de Salida           │                                           │
│  │  ┌──────┬──────┬────────┐│                                           │
│  │  │KAFKA │OUTBOX│ DUAL   ││                                           │
│  │  │  ●   │      │        ││                                           │
│  │  └──────┴──────┴────────┘│                                           │
│  │                           │                                           │
│  └───────────────────────────┘                                           │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

**Detalles visuales**:
- **Domain Cards**: Cada card tiene un **borde izquierdo de 3px** en el color del dominio (emerald para Healthcare, blue para Ecommerce, etc.). Al hacer hover, un **glow sutil** del color del dominio aparece detrás de la card.
- **Slider de EPS**: Track con gradiente de `--accent-cyan` a `--accent-violet`. El thumb es un círculo con sombra que al arrastrar muestra un **tooltip flotante** con el valor actual. El valor cambia en tiempo real y se refleja inmediatamente en el motor.
- **Slider de Error Rate**: Track con gradiente de `--accent-emerald` (0%) a `--accent-rose` (100%). Comunica visualmente el riesgo.
- **Tabs Sink de Salida**: Tres opciones ("KAFKA", "OUTBOX", "DUAL") como **segmented control** con animación de slide del fondo activo. Al seleccionar "OUTBOX" o "DUAL", aparece debajo la configuración de base de datos (JDBC URL, pool size) con una animación de expansión.
- **Botón "+ Añadir"**: Abre un modal con los dominios disponibles como **grid de cards con iconos**. Los dominios ya activos aparecen con un checkmark y deshabilitados.

---

### 5.3 Vista: IA Studio (Editor de Esquemas)

**Ruta**: `/ai-studio`

**Propósito**: Crear dominios custom con IA, revisar y editar esquemas de eventos de cualquier dominio, refinar con el LLM.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│  🧠 IA Studio                                    Provider: OpenAI ▼     │
│  ═══════════                                      Model: gpt-4o   ▼     │
│                                                                          │
│  ┌─ PANEL IZQUIERDO (Chat / Prompt) ────────────────────────────────┐   │
│  │                                                                   │   │
│  │  Crear Dominio Custom                                             │   │
│  │  ─────────────────────                                            │   │
│  │                                                                   │   │
│  │  Describe tu dominio de negocio:                                  │   │
│  │  ┌───────────────────────────────────────────────────────────┐   │   │
│  │  │  Quiero generar eventos de una plataforma de trading      │   │   │
│  │  │  de criptomonedas. Necesito eventos de órdenes de compra  │   │   │
│  │  │  y venta, precios en tiempo real, alertas de volatilidad  │   │   │
│  │  │  y liquidaciones forzadas.                                 │   │   │
│  │  │                                                            │   │   │
│  │  └───────────────────────────────────────────────────────────┘   │   │
│  │                                                                   │   │
│  │  ┌────────────────────────────────────┐                          │   │
│  │  │  ✨  Generar Esquema con IA         │ ← botón violeta          │   │
│  │  └────────────────────────────────────┘   con sparkle icon        │   │
│  │                                                                   │   │
│  │  ─── Historial de Conversación ───                                │   │
│  │                                                                   │   │
│  │  🤖  He generado un esquema con 5 eventos normales               │   │
│  │      y 3 de error para el dominio "crypto-trading".              │   │
│  │      Revisa el esquema a la derecha.                              │   │
│  │                                                                   │   │
│  │  👤  Añade un campo "leverageMultiplier" al evento                │   │
│  │      de LiquidationForced.                                        │   │
│  │                                                                   │   │
│  │  🤖  Actualizado. He añadido leverageMultiplier (double)          │   │
│  │      con rango 2x-125x.                                          │   │
│  │                                                                   │   │
│  └───────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  ┌─ PANEL DERECHO (Schema Viewer / Editor) ─────────────────────────┐   │
│  │                                                                   │   │
│  │  Esquema: crypto-trading                    [Guardar] [Descartar]│   │
│  │  ─────────────────────────                                        │   │
│  │                                                                   │   │
│  │  📄 Eventos Normales (5)                                          │   │
│  │  ├── 📋 OrderPlaced                                               │   │
│  │  │   ├── orderId       string   [required]  faker: uuid          │   │
│  │  │   ├── pair          string   [required]  enum: BTC/USDT...   │   │
│  │  │   ├── side          enum     [required]  BUY, SELL            │   │
│  │  │   ├── price         double   [required]  range: 0.01-100000  │   │
│  │  │   ├── quantity      double   [required]  range: 0.001-1000   │   │
│  │  │   └── timestamp     datetime [required]  faker: now           │   │
│  │  ├── 📋 PriceUpdate                                               │   │
│  │  │   ├── ...                                                      │   │
│  │  ├── 📋 TradeExecuted                                             │   │
│  │  ├── 📋 VolatilityAlert                                           │   │
│  │  └── 📋 PortfolioSnapshot                                         │   │
│  │                                                                   │   │
│  │  ⚠️ Eventos de Error (3)                                          │   │
│  │  ├── ❌ OrderRejected                                              │   │
│  │  ├── ❌ LiquidationForced                                          │   │
│  │  │   ├── accountId     string   [required]                        │   │
│  │  │   ├── pair          string   [required]                        │   │
│  │  │   ├── lossAmount    double   [required]                        │   │
│  │  │   ├── leverageMultiplier  double  [required]  range: 2-125   │   │
│  │  │   └── ...                                                      │   │
│  │  └── ❌ ExchangeTimeout                                            │   │
│  │                                                                   │   │
│  │  ── Acciones ──                                                   │   │
│  │  [+ Añadir Campo]  [+ Añadir Evento]  [📥 Exportar JSON]        │   │
│  │                                                                   │   │
│  └───────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

**Detalles visuales**:
- **Chat panel**: Estilo de chat moderno con burbujas. Las del bot (🤖) tienen fondo `--surface-2` con un borde izquierdo violeta. Las del usuario tienen fondo `--surface-3`.
- **Botón "Generar con IA"**: Gradiente violeta con un **icono de sparkle (✨) animado** que rota suavemente. Al hacer click, un **shimmer effect** recorre el botón de izquierda a derecha.
- **Tree View del esquema**: Cada nodo es expandible/colapsable con animación. Los campos `[required]` tienen un badge cyan. Los campos editables muestran un icono de lápiz al hacer hover.
- **Inline editing**: Al hacer click en un tipo o faker expression, se convierte en un input editable in-place con autocompletado.
- Si la IA no está configurada, el panel izquierdo muestra una **empty state** elegante con instrucciones para configurar OpenAI/Ollama, y el panel derecho muestra los esquemas de dominios existentes en modo solo lectura.

---

### 5.4 Vista: Centro de Control / Dashboard en Vivo

**Ruta**: `/dashboard`

**Propósito**: Vista principal durante la publicación activa. Muestra métricas en tiempo real, gráficos, estado de cada dominio/topic, y controles de operación.

> [!IMPORTANT]
> Esta es la vista más compleja y la que genera el mayor efecto "WOW". Los datos llegan vía **WebSocket** y se actualizan cada 200-500ms.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│  ┌─ HERO METRICS BAR (glassmorphism, fixed top) ────────────────────────┐   │
│  │                                                                       │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ │   │
│  │  │   1,247,832  │ │     12,483   │ │    342.5     │ │     8.2ms    │ │   │
│  │  │  Total Events│ │  Total Errors│ │  Events/sec  │ │  Avg Latency │ │   │
│  │  │  ↑ odómetro  │ │  ↑ odómetro  │ │  ↑ sparkline │ │  ↑ sparkline │ │   │
│  │  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘ │   │
│  │                                                                       │   │
│  │  Uptime: 00:42:18    │  Engine: ▶ RUNNING    │   ⏸  ⏹  ⚡ Speed     │   │
│  │                                                                       │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─ REAL-TIME CHARTS (2 columnas) ──────────────────────────────────────┐   │
│  │                                                                       │   │
│  │  ┌─ Throughput (EPS) ──────────────┐  ┌─ Latencia por Topic ────────┐│   │
│  │  │                                  │  │                             ││   │
│  │  │    350 ┤                     ╱╲  │  │   25ms ┤                    ││   │
│  │  │    300 ┤                ╱╲╱╱╲╱  │  │   20ms ┤    ╱╲              ││   │
│  │  │    250 ┤           ╱╲╱╱         │  │   15ms ┤  ╱╱  ╲╱╲          ││   │
│  │  │    200 ┤      ╱╲╱╱╱             │  │   10ms ┤╱╱       ╲╲╱╲     ││   │
│  │  │    150 ┤  ╱╲╱╱                  │  │    5ms ┤            ╲╲╱── ││   │
│  │  │    100 ┤╱╱                      │  │    0ms ┤                    ││   │
│  │  │        └────────────────────────│  │        └────────────────────││   │
│  │  │  ── healthcare ── ecommerce     │  │  ── healthcare ── ecommerce ││   │
│  │  │  ── fastfood   ── total         │  │  ── fastfood                ││   │
│  │  └──────────────────────────────────┘  └─────────────────────────────┘│   │
│  │                                                                       │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─ DOMAIN STREAM CARDS ────────────────────────────────────────────────┐   │
│  │                                                                       │   │
│  │  ┌─ Healthcare ● ACTIVE ──────────────────────────────────────────┐  │   │
│  │  │                                                                 │  │   │
│  │  │  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌────────────┐  │  │   │
│  │  │  │  498,234   │  │   49,823  │  │  50.1/s   │  │   6.2ms    │  │  │   │
│  │  │  │  Events    │  │  Errors   │  │  EPS      │  │  Latency   │  │  │   │
│  │  │  └───────────┘  └───────────┘  └───────────┘  └────────────┘  │  │   │
│  │  │                                                                 │  │   │
│  │  │  Topic: healthcare-events          Error Topic: healthcare-err  │  │   │
│  │  │  Sink: KAFKA                       Mode: STEADY                 │  │   │
│  │  │  Key: patientId                    Format: JSON                 │  │   │
│  │  │                                                                 │  │   │
│  │  │  ┌─ MINI SPARKLINE ────────────────────────────────────────┐   │  │   │
│  │  │  │  ╱╲╱╱╲╱╲╱╱╲╱╲╱╱╲╱╲╱╲╱╱╲╱╲  (últimos 60s de EPS)     │   │  │   │
│  │  │  └──────────────────────────────────────────────────────────┘   │  │   │
│  │  │                                                                 │  │   │
│  │  │  [⏸ Pausar]  [⚡ EPS: 50]  [⚠ Err: 10%]  [⏹ Detener]         │  │   │
│  │  └─────────────────────────────────────────────────────────────────┘  │   │
│  │                                                                       │   │
│  │  ┌─ Ecommerce ● ACTIVE ──────────────────────────────────────────┐  │   │
│  │  │  ... (misma estructura con colores azul)                        │  │   │
│  │  └─────────────────────────────────────────────────────────────────┘  │   │
│  │                                                                       │   │
│  │  ┌─ FastFood 🟠 PAUSED ──────────────────────────────────────────┐  │   │
│  │  │  ... (misma estructura con colores naranja, opacidad reducida)  │  │   │
│  │  └─────────────────────────────────────────────────────────────────┘  │   │
│  │                                                                       │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─ EVENT FEED (últimos 20 eventos, scroll automático) ─────────────────┐   │
│  │                                                                       │   │
│  │  12:45:23.456  ● healthcare  NORMAL   PatientAdmission    8.2ms     │   │
│  │  12:45:23.458  ● ecommerce   NORMAL   OrderCreated        5.1ms     │   │
│  │  12:45:23.461  ● healthcare  ERROR    CriticalVitals      9.0ms     │   │
│  │  12:45:23.463  ● fastfood    NORMAL   DriveThruOrder      4.8ms     │   │
│  │  12:45:23.465  ● ecommerce   ERROR    PaymentFailed       7.3ms     │   │
│  │  ...                                                                  │   │
│  │                                                                       │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Detalles visuales del Dashboard**:

- **Hero Metrics Bar**: Panel con `backdrop-filter: blur(12px)` y `background: rgba(18,18,26,0.75)`. Los números grandes usan **JetBrains Mono Bold 32px** con efecto **odómetro** (los dígitos rotan verticalmente al cambiar). Cada métrica tiene un **mini sparkline** (línea de 60 puntos) debajo del número que muestra la tendencia de los últimos 60 segundos.

- **Gráficos en Tiempo Real**: Líneas suavizadas (spline) con relleno degradado semi-transparente debajo de cada línea. Cada dominio tiene su color exclusivo. El gráfico hace **scroll horizontal** mostrando los últimos 5 minutos. Los puntos nuevos entran con una animación de **draw** (la línea se extiende). Al pasar el cursor, un **crosshair vertical** muestra los valores exactos en un tooltip flotante.

- **Domain Stream Cards**: Cada card tiene un **borde izquierdo grueso (4px)** en el color del dominio. El punto de estado (●) **pulsa suavemente** cuando el dominio está activo. Si está pausado, la card entera tiene `opacity: 0.6` y el punto de estado es ámbar estático. El **mini sparkline** dentro de cada card es una línea simplificada de color del dominio que da contexto rápido sin tener que mirar los gráficos grandes.

- **Botones de control inline**: Al hacer click en `[⚡ EPS: 50]`, aparece un **popover** con un slider para cambiar la velocidad sin salir del dashboard. El cambio se aplica inmediatamente al motor vía WebSocket.

- **Event Feed**: Tabla con **scroll automático** (auto-scroll se pausa si el usuario hace scroll manual). Los eventos de error tienen la fila con un fondo `rgba(244,63,94,0.08)` (rojo sutil). El nombre del dominio tiene un **dot de color** a la izquierda. Los eventos nuevos entran con un **fade-in** desde abajo.

- **Glow ambiental de fondo**: Un `div` detrás de todo con un `radial-gradient` que cambia de color según el estado global (cyan = running, amber = paused, red = error). Opacity muy baja (0.05-0.1), apenas perceptible pero da sensación de "vida".

---

### 5.5 Vista: Pipeline Outbox & Relay

**Ruta**: `/outbox`

**Propósito**: Visualizar el pipeline completo de persistencia: eventos → PostgreSQL (negocio + outbox) → Relay → Kafka. Mostrar métricas del Outbox Publisher, el Relay y el estado del Circuit Breaker.

> [!IMPORTANT]
> Esta vista es exclusiva de OmniStreamForce y no existe en herramientas similares. Debe comunicar visualmente el flujo de datos a través de las caplas de persistencia.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│  📦 Pipeline Outbox & Relay                                                 │
│  ══════════════════════════                                                  │
│                                                                              │
│  ┌─ TOPOLOGÍA VISUAL DEL PIPELINE ──────────────────────────────────────┐   │
│  │                                                                       │   │
│  │   ┌──────────┐      ┌──────────────┐      ┌──────────┐              │   │
│  │   │  Engine   │─────▶│  PostgreSQL   │─────▶│  Relay    │──────▶ 🟢  │   │
│  │   │ 342 ev/s │      │              │      │          │        Kafka │   │
│  │   └──────────┘      │  ┌─────────┐ │      │  ┌─────┐ │              │   │
│  │                      │  │ Business│ │      │  │Poll │ │              │   │
│  │   ● Circuit: CLOSED  │  │  Table  │ │      │  │Claim│ │              │   │
│  │                      │  └─────────┘ │      │  │Pub  │ │              │   │
│  │   Queue Depth: 23    │  ┌─────────┐ │      │  │Mark │ │              │   │
│  │                      │  │ Outbox  │ │      │  └─────┘ │              │   │
│  │                      │  │  Table  │ │      │          │              │   │
│  │                      │  └─────────┘ │      │  Lag:    │              │   │
│  │                      └──────────────┘      │  0.8s    │              │   │
│  │                                             └──────────┘              │   │
│  │                                                                       │   │
│  │   ──── flow animation: puntos cyan viajando por las flechas ────     │   │
│  │                                                                       │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─ MÉTRICAS (3 columnas) ──────────────────────────────────────────────┐   │
│  │                                                                       │   │
│  │  ┌─ Outbox Publisher ──────┐ ┌─ Relay ──────────────┐ ┌─ Health ───┐│   │
│  │  │                         │ │                       │ │            ││   │
│  │  │  Enqueued:    1,247,832 │ │  Polls:       24,891 │ │  Circuit:  ││   │
│  │  │  Written:     1,247,810 │ │  Claimed:  1,247,810 │ │  🟢 CLOSED ││   │
│  │  │  Failed:             22 │ │  Published:1,247,798 │ │            ││   │
│  │  │  Dropped:             0 │ │  Failed:          12 │ │  Queue:    ││   │
│  │  │  Retries:            34 │ │  Dead-letter:      0 │ │  23 / 1024 ││   │
│  │  │                         │ │  Purged:   1,200,000 │ │  ██░░░ 2%  ││   │
│  │  │  Batches:        12,478 │ │                       │ │            ││   │
│  │  │  Avg Batch:        100  │ │  Pending:         12 │ │  DB Pool:  ││   │
│  │  │  Avg Commit:      4.2ms │ │  Lag:           0.8s │ │  8 / 10    ││   │
│  │  │                         │ │  Avg Batch:     50.1 │ │  ████░ 80% ││   │
│  │  │                         │ │  Avg Latency:  12ms  │ │            ││   │
│  │  └─────────────────────────┘ └───────────────────────┘ └────────────┘│   │
│  │                                                                       │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─ GRÁFICOS ───────────────────────────────────────────────────────────┐   │
│  │                                                                       │   │
│  │  ┌─ Throughput DB ──────────────┐  ┌─ Lag del Relay (segundos) ────┐│   │
│  │  │  (filas escritas/s)           │  │                               ││   │
│  │  │  340 ┤      ╱╲╱╲╱╲           │  │  2.0 ┤                        ││   │
│  │  │  320 ┤  ╱╲╱╱      ╲╱╲        │  │  1.5 ┤  ╱╲                    ││   │
│  │  │  300 ┤╱╱              ╲╱──   │  │  1.0 ┤╱╱  ╲╲╱╱──             ││   │
│  │  │      └────────────────────────│  │  0.5 ┤          ╲──          ││   │
│  │  │  ── written  ── failed        │  │  0.0 ┤                        ││   │
│  │  └───────────────────────────────┘  └───────────────────────────────┘│   │
│  │                                                                       │   │
│  │  ┌─ Circuit Breaker Timeline ────────────────────────────────────┐  │   │
│  │  │  12:40 ────🟢──────────🔴──🟢────────────────🟢───── 12:47   │  │   │
│  │  │             CLOSED     OPEN  HALF   CLOSED                     │  │   │
│  │  └────────────────────────────────────────────────────────────────┘  │   │
│  │                                                                       │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─ TABLAS POR DOMINIO (pestaña) ───────────────────────────────────────┐   │
│  │                                                                       │   │
│  │  [Healthcare] [Ecommerce] [FastFood]                                  │   │
│  │                                                                       │   │
│  │  Tabla: osf_healthcare_events                                         │   │
│  │  ┌─────────────┬─────────────┬─────────┬───────────┬────────────┐    │   │
│  │  │ Columna     │ Tipo SQL    │ Source  │ Declared? │ Nulls %    │    │   │
│  │  ├─────────────┼─────────────┼─────────┼───────────┼────────────┤    │   │
│  │  │ event_id    │ TEXT PK     │ envelope│ ✓         │ 0%         │    │   │
│  │  │ patient_id  │ TEXT        │ payload │ ✓         │ 45%        │    │   │
│  │  │ heart_rate  │ BIGINT      │ payload │ ✓         │ 60%        │    │   │
│  │  │ payload     │ JSONB       │ overflow│ always    │ 0%         │    │   │
│  │  │ payload_extra│ JSONB      │ overflow│ always    │ 82%        │    │   │
│  │  └─────────────┴─────────────┴─────────┴───────────┴────────────┘    │   │
│  │                                                                       │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Detalles visuales del Pipeline**:

- **Topología visual**: Un diagrama SVG con nodos (Engine → PostgreSQL → Relay → Kafka) conectados por flechas animadas. **Puntos cyan** (pequeños círculos) viajan por las flechas simulando el flujo de datos. La velocidad de los puntos es proporcional al throughput real. Si el circuit breaker se abre, la flecha Engine → PostgreSQL **parpadea en rojo** y los puntos se detienen.

- **Circuit Breaker visual**: Un indicador prominente que muestra `🟢 CLOSED` (verde), `🟡 HALF-OPEN` (ámbar), o `🔴 OPEN` (rojo con pulso). Al transicionar entre estados, hay una animación de color morph.

- **Lag Gauge**: El lag del relay (0.8s) se puede mostrar como un **gauge circular** (tipo velocímetro) con zonas de color: verde (< 1s), ámbar (1-5s), rojo (> 5s).

- **Queue Depth**: Barra de progreso horizontal que se llena en tiempo real. Cambia de color si se acerca al límite.

---

### 5.6 Vista: Configuración y Perfiles

**Ruta**: `/settings`

**Propósito**: Gestión de perfiles YAML, configuración global, conexiones guardadas.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│  ⚙️  Configuración                                                      │
│  ═══════════════                                                         │
│                                                                          │
│  ┌─ TABS ────────────────────────────────────────────────────────────┐  │
│  │  [Perfiles]  [Conexiones]  [IA]  [Avanzado]                       │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ── PERFILES ──                                                          │
│                                                                          │
│  ┌─ Perfiles Predefinidos ───────────────────────────────────────────┐  │
│  │                                                                    │  │
│  │  ┌───────────────────┐  ┌───────────────────┐  ┌────────────────┐│  │
│  │  │ ⚡ Healthcare Fast │  │ 💥 Ecommerce Burst│  │ 🔋 Energy Low  ││  │
│  │  │   100 EPS, 5% err │  │   500 EPS, 15% err│  │   1 EPS, 2% err││  │
│  │  │   STEADY, JSON    │  │   BURST, JSON     │  │   STEADY, JSON ││  │
│  │  │     [Cargar]      │  │     [Cargar]      │  │    [Cargar]    ││  │
│  │  └───────────────────┘  └───────────────────┘  └────────────────┘│  │
│  │                                                                    │  │
│  │  ┌───────────────────┐  ┌───────────────────┐  ┌────────────────┐│  │
│  │  │ 🚗 Highway Spike  │  │ 🏗  Development   │  │ ☁️ MSK Multi   ││  │
│  │  │   50 EPS, 20% err │  │   5 EPS, 50% err  │  │  2 domains, IAM││  │
│  │  │   SPIKE, JSON     │  │   STEADY, JSON    │  │   STEADY, JSON ││  │
│  │  │     [Cargar]      │  │     [Cargar]      │  │    [Cargar]    ││  │
│  │  └───────────────────┘  └───────────────────┘  └────────────────┘│  │
│  │                                                                    │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌─ Editor YAML ─────────────────────────────────────────────────────┐  │
│  │                                                                    │  │
│  │  ┌──────────────────────────────────────────────────────────────┐ │  │
│  │  │  1 │ kafka:                                                   │ │  │
│  │  │  2 │   clusterType: MSK                                      │ │  │
│  │  │  3 │   bootstrapServers: "b-1.cluster.xxx.kafka..."          │ │  │
│  │  │  4 │   mskConfig:                                            │ │  │
│  │  │  5 │     authType: IAM                                       │ │  │
│  │  │  6 │     awsRegion: us-east-1                                │ │  │
│  │  │  7 │ domainTopicMappings:                                     │ │  │
│  │  │  8 │   - domain: ecommerce                                    │ │  │
│  │  │  9 │     topicName: ecommerce-orders                          │ │  │
│  │  │ 10 │     eventsPerSecond: 50                                  │ │  │
│  │  │ .. │                                                          │ │  │
│  │  └──────────────────────────────────────────────────────────────┘ │  │
│  │                                                                    │  │
│  │  [📥 Importar YAML]  [📤 Exportar YAML]  [💾 Guardar Perfil]     │  │
│  │                                                                    │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

**Detalles visuales**:
- **Tarjetas de perfil**: Cada una con un emoji significativo y un borde sutil. Al hacer hover, muestra un **preview** del YAML en un tooltip. Al hacer click en "Cargar", un **toast** confirma la carga con los parámetros clave.
- **Editor YAML**: Editor de código embebido con syntax highlighting (colores acordes al dark theme), números de línea, y validación inline (errores en rojo, warnings en ámbar).

---

### 5.7 Vista: Conexiones (Kafka & Base de Datos)

**Ruta**: `/connections`

**Propósito**: Gestionar las conexiones a Kafka y a PostgreSQL (para Outbox). Probar, guardar, y cambiar entre conexiones.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│  🔌 Conexiones                                     [+ Nueva Conexión]   │
│  ═══════════════                                                         │
│                                                                          │
│  ┌─ KAFKA ───────────────────────────────────────────────────────────┐  │
│  │                                                                    │  │
│  │  ┌─────────────────────────────────────────────────────────────┐  │  │
│  │  │  🟢 kafka-local                                ● ACTIVA     │  │  │
│  │  │  localhost:9092 | PLAINTEXT | 3 brokers | KRaft v3.8.0     │  │  │
│  │  │  Topics: 12 | Última prueba: hace 2 min                    │  │  │
│  │  │                                                             │  │  │
│  │  │  [🔌 Reconectar]  [✏️ Editar]  [🗑️ Eliminar]              │  │  │
│  │  └─────────────────────────────────────────────────────────────┘  │  │
│  │                                                                    │  │
│  │  ┌─────────────────────────────────────────────────────────────┐  │  │
│  │  │  ⚪ msk-prod-us-east-1                         ○ INACTIVA   │  │  │
│  │  │  b-1.cluster.xxx...9094 | IAM | 6 brokers | MSK v3.6.0    │  │  │
│  │  │  Topics: 24 | Última prueba: hace 1 día                    │  │  │
│  │  │                                                             │  │  │
│  │  │  [🔌 Activar]  [✏️ Editar]  [🗑️ Eliminar]                 │  │  │
│  │  └─────────────────────────────────────────────────────────────┘  │  │
│  │                                                                    │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌─ BASE DE DATOS (Outbox) ──────────────────────────────────────────┐  │
│  │                                                                    │  │
│  │  ┌─────────────────────────────────────────────────────────────┐  │  │
│  │  │  🟢 postgres-local                             ● ACTIVA     │  │  │
│  │  │  localhost:5432/omnistreamforce | Pool: 8/10 | v16.3        │  │  │
│  │  │  Tablas OSF: 3 | Outbox pendientes: 12                     │  │  │
│  │  │                                                             │  │  │
│  │  │  [🔌 Reconectar]  [✏️ Editar]  [🗑️ Eliminar]              │  │  │
│  │  └─────────────────────────────────────────────────────────────┘  │  │
│  │                                                                    │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Componentes Reutilizables (Component Library)

| Componente | Descripción | Variantes |
|---|---|---|
| **MetricCard** | Card con número grande (odómetro), label, y sparkline opcional | `size: sm/md/lg`, `color: domain color`, `trend: up/down/neutral` |
| **DomainBadge** | Pill con dot de color + nombre del dominio | `variant: active/paused/stopped` |
| **StatusIndicator** | Punto pulsante + texto de estado | `status: running/paused/stopped/error` |
| **SliderControl** | Slider con tooltip flotante y gradiente en track | `type: eps/errorRate/generic`, `min/max/step` |
| **StreamCard** | Card completa de un dominio con métricas y controles inline | `state: active/paused`, `domain: string` |
| **RealtimeChart** | Gráfico de líneas con scroll temporal y multi-series | `type: line/area`, `series: TopicStats[]` |
| **SparkLine** | Mini gráfico inline (60 puntos, sin ejes) | `color: string`, `data: number[]` |
| **EventRow** | Fila del feed de eventos con dot de color, timestamp, tipo | `type: normal/error`, `domain: string` |
| **CodeEditor** | Editor de código con syntax highlighting (YAML/JSON) | `language: yaml/json`, `readonly: boolean` |
| **SchemaTree** | Árbol expandible de campos de un EventSchema | `editable: boolean`, `onEdit: callback` |
| **TopologyDiagram** | SVG con nodos y flechas animadas para el pipeline | `nodes: Node[]`, `edges: Edge[]` |
| **GlassPanel** | Contenedor con backdrop-filter blur y borde translúcido | `elevation: 1/2/3` |
| **SegmentedControl** | Grupo de opciones mutuamente exclusivas con slide animado | `options: string[]`, `selected: number` |
| **OdometerNumber** | Número con dígitos que rotan verticalmente al cambiar | `value: number`, `format: integer/decimal` |
| **PulseGlow** | Círculo que pulsa con box-shadow animado | `color: string`, `speed: fast/slow` |
| **Toast** | Notificación temporal con barra de progreso | `type: success/error/warning/info` |

---

## 7. Diseño Responsive

| Breakpoint | Layout | Adaptaciones |
|---|---|---|
| **≥ 1440px** | Full: sidebar + content + panel derecho | Todos los gráficos y paneles visibles |
| **1024–1439px** | sidebar colapsada a 64px + content | Panel derecho se oculta, accesible vía drawer |
| **768–1023px** | Sin sidebar, nav como top bar hamburger + content | Gráficos apilados en 1 columna. Domain cards apiladas |
| **< 768px** | Mobile: nav bottom, content full-width | Métricas en carousel horizontal. Gráficos simplificados. Feed de eventos en vista compacta |

---

## 8. Arquitectura Técnica (Backend para Web)

### 8.1 Nuevo Módulo: `omnistreamforce-web`

| Aspecto | Decisión |
|---|---|
| **Servidor HTTP** | **Javalin 6** (ligero, WebSockets nativos, servlet-free). Alternativa: Vert.x. |
| **API REST** | Endpoints JSON para configuración, conexiones, dominios, perfiles, test de conexión. |
| **WebSocket** | Canal `/ws/dashboard` que emite snapshots de `GenerationStats`, `PersistenceMetrics`, y `RelayStats` cada 300ms. |
| **Servir Frontend** | El fat JAR sirve los assets estáticos del frontend desde `/static` (embebidos en el JAR vía `src/main/resources/static/`). |
| **Comando CLI** | `omnistreamforce web --port 8080` arranca el servidor web en vez del flujo interactivo CLI. |

### 8.2 API REST (Resumen de Endpoints)

```
GET    /api/connections                  → lista conexiones guardadas
POST   /api/connections/test             → prueba una conexión Kafka o DB
POST   /api/connections                  → guarda conexión
DELETE /api/connections/:id              → elimina conexión

GET    /api/domains                      → lista dominios disponibles (registry)
GET    /api/domains/:name/schema         → esquema de un dominio

POST   /api/streams                      → crea stream (dominio+topic+config)
DELETE /api/streams/:domain              → elimina stream
PATCH  /api/streams/:domain              → modifica EPS, errorRate, etc. en caliente
POST   /api/streams/:domain/pause        → pausa dominio
POST   /api/streams/:domain/resume       → reanuda dominio

POST   /api/engine/start                 → arranca el motor con los streams configurados
POST   /api/engine/stop                  → detiene todo
GET    /api/engine/status                → estado actual del motor

GET    /api/topics                       → lista topics del cluster conectado

POST   /api/ai/propose-schema            → propone esquema vía LLM
POST   /api/ai/refine                    → refina esquema con mensaje del usuario

GET    /api/profiles                     → lista perfiles YAML
POST   /api/profiles                     → guarda perfil
GET    /api/profiles/:name               → carga perfil
DELETE /api/profiles/:name               → elimina perfil

GET    /api/outbox/metrics               → métricas de PersistenceMetrics
GET    /api/relay/stats                  → métricas de RelayStats

WebSocket  /ws/dashboard                 → stream de métricas en tiempo real
```

### 8.3 Frontend (SPA)

| Aspecto | Decisión |
|---|---|
| **Framework** | **React 19** con Vite 6 (build rápido, HMR, empaquetado estático trivial). |
| **Styling** | **Vanilla CSS** con Custom Properties (design tokens). Sin Tailwind. |
| **Estado** | React Context + `useReducer` para estado global (conexión, streams, engine status). |
| **Gráficos** | **Recharts** (React-native, performant, composable). |
| **Iconos** | **Lucide React** |
| **Tipografía** | Google Fonts: Inter + JetBrains Mono |
| **WebSocket** | Hook custom `useWebSocket` con reconnect automático y buffer de snapshots. |
| **Code Editor** | **CodeMirror 6** para el editor YAML/JSON. |
| **Routing** | React Router v7. |
| **Build output** | `dist/` se copia a `omnistreamforce-web/src/main/resources/static/` en el build Maven. |

---

## 9. Mapeo Features Existentes → Vistas Web

| Feature del CLI / Core | Vista Web | Componente |
|---|---|---|
| **Conexión a Kafka** (Local, MSK, Confluent) | Wizard Paso 1 + `/connections` | Formularios dinámicos + test de conexión |
| **Topic selection interactivo** | Wizard Paso 2 + `/streams` (dropdown) | Dropdown con autocomplete del cluster |
| **Multi-dominio/multi-topic** | `/streams` | Domain Cards + formularios de mapping |
| **Control de EPS y Error Rate** | `/streams` + `/dashboard` inline | Sliders + popovers en dashboard |
| **Modos de publicación** (STEADY/BURST/SPIKE/RAMP) | `/streams` | SegmentedControl |
| **Key Strategy** (Random/EntityID/RoundRobin) | `/streams` | Radio buttons |
| **Serialización** (JSON/Avro/Protobuf) | `/streams` | Dropdown |
| **Motor de generación con virtual threads** | `/dashboard` (métricas en vivo) | Hero Metrics + Charts + Stream Cards |
| **Error Injection** | `/dashboard` (contadores de error) + `/streams` (config) | MetricCards + Sliders |
| **IA: proponer esquemas** | `/ai-studio` | Chat panel + Schema Tree |
| **IA: refinar esquemas** | `/ai-studio` | Chat conversacional |
| **Dominios**: Healthcare, Ecommerce, FastFood, Energy, Autos, Highway | `/streams` (selector) + `/ai-studio` (viewer) | Domain selector + Schema viewer |
| **Outbox Publisher** (JdbcOutboxPublisher) | `/outbox` (métricas) + `/streams` (sink selector) | Topología + MetricCards |
| **Relay** (OutboxRelay, polling, purge) | `/outbox` (métricas, lag) | Lag Gauge + Timeline |
| **Circuit Breaker** | `/outbox` (indicador visual) | StatusIndicator con timeline |
| **PersistenceMetrics** | `/outbox` | Métricas en paneles |
| **RelayStats** | `/outbox` | Métricas en paneles |
| **DDL generado desde EventSchema** | `/outbox` (tabla de columnas) | Tabla de columnas SQL |
| **Perfiles YAML** | `/settings` | Cards + Editor YAML |
| **Configuración global** | `/settings` | Formularios |
| **Pause/Resume/Stop** | `/dashboard` (botones) | Botones animados |
| **Add/Remove dominio en caliente** | `/dashboard` + `/streams` | Cards con animación de entrada/salida |
| **Estadísticas por topic** | `/dashboard` (per-topic breakdown) | Tabla + sparklines |
| **Event Feed en vivo** | `/dashboard` (tabla scroll) | EventRow con auto-scroll |

---

## 10. Plan de Implementación (Fases)

### Fase W1: Infraestructura Web (Backend)
- Crear módulo Maven `omnistreamforce-web`.
- Javalin server con API REST básica (health, connections, domains).
- WebSocket `/ws/dashboard` emitiendo snapshots de `GenerationStats`.
- Comando CLI `web` para arrancar el servidor.

### Fase W2: Frontend Foundation
- Proyecto React + Vite en `omnistreamforce-ui/`.
- Design system completo en CSS (tokens, tipografía, grid, componentes base).
- Layout principal (sidebar + header + content area).
- Routing entre vistas.
- Hook `useWebSocket` con reconnect.

### Fase W3: Wizard de Conexión + Constructor de Streams
- Wizard de 4 pasos (conexión, dominios, esquemas, lanzar).
- Formularios dinámicos para Kafka (Local/MSK/Confluent) y DB (Outbox).
- Topic selector con datos del cluster.
- Domain cards con sliders de EPS y error rate.
- Sink selector (KAFKA/OUTBOX/DUAL).

### Fase W4: Dashboard en Vivo
- Hero metrics bar con OdometerNumber y sparklines.
- Gráficos de EPS y latencia en tiempo real con Recharts.
- Domain stream cards con controles inline.
- Event feed con auto-scroll.
- Glow ambiental de fondo.

### Fase W5: IA Studio + Pipeline Outbox
- Chat panel para proponer/refinar esquemas.
- Schema tree viewer/editor.
- Vista de pipeline outbox con topología animada SVG.
- Métricas de PersistenceMetrics y RelayStats.
- Circuit breaker visual y lag gauge.

### Fase W6: Settings + Polish
- Perfiles YAML con CodeMirror.
- Gestión de conexiones.
- Command palette (Ctrl+K).
- Responsive design (mobile breakpoints).
- Micro-animaciones finales.
- Testing E2E.

---

## Verification Plan

### Automated Tests
- `mvn test` en `omnistreamforce-web`: endpoints REST, serialización de stats a JSON, WebSocket handshake.
- Tests de componentes React (Vitest + Testing Library): MetricCard, SliderControl, OdometerNumber, charts data binding.

### Manual Verification
1. `java -jar omnistreamforce-cli.jar web --port 8080` → Abrir `http://localhost:8080`.
2. Completar wizard de conexión a un cluster Kafka local (docker-compose).
3. Añadir 3 dominios (Healthcare, Ecommerce, FastFood) con EPS y error rates diferentes.
4. Iniciar generación → verificar que el dashboard muestra métricas en vivo con gráficos fluyendo.
5. Pausar un dominio desde el dashboard → verificar que la card cambia visualmente y el gráfico refleja la pausa.
6. Cambiar EPS de un dominio en caliente desde el slider inline del dashboard.
7. Configurar sink DUAL → verificar métricas del outbox en `/outbox` con topología animada.
8. Usar IA Studio para crear un dominio custom → verificar que el esquema se refleja en el selector de dominios.
9. Verificar responsive en móvil (DevTools 375px).
