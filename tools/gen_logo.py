"""Usage: python3 tools/gen_logo.py app/src/main/res <preview-dir>

Generates the Dear logo: a dot-matrix "D" followed by a red full stop ("D.").

Writes the adaptive-icon foreground/monochrome vector drawables, the notification icon and an SVG
preview. Geometry is in the 108x108 adaptive-icon viewport (content kept inside the 66 dp safe zone).
"""
import sys
from pathlib import Path

res = Path(sys.argv[1])
preview_dir = Path(sys.argv[2])

# 5 x 7 dot-matrix "D"
D = [
    "XXXX.",
    "X...X",
    "X...X",
    "X...X",
    "X...X",
    "X...X",
    "XXXX.",
]
WHITE = "#FFFFFFFF"
RED = "#FFD71921"


def dots(size, pitch, r, red_r, stop_col):
    """Dot centres for the D plus the red stop, centred in a size x size box."""
    width = stop_col * pitch + 2 * r
    height = 6 * pitch + 2 * r
    x0 = (size - width) / 2 + r
    y0 = (size - height) / 2 + r
    white = [(x0 + c * pitch, y0 + row * pitch) for row, line in enumerate(D) for c, ch in enumerate(line) if ch == "X"]
    stop = (x0 + stop_col * pitch, y0 + 6 * pitch + (r - red_r))
    return white, stop


def circle_path(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0"


def vector(size, groups):
    paths = "\n".join(
        f'    <path\n        android:fillColor="{color}"\n        android:pathData="{"".join(circle_path(x, y, r) for x, y in pts)}" />'
        for color, r, pts in groups
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Generated: dot-matrix "D." logo -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{size}dp"\n    android:height="{size}dp"\n'
        f'    android:viewportWidth="{size}"\n    android:viewportHeight="{size}">\n'
        f"{paths}\n</vector>\n"
    )


# Launcher: 108 viewport, pitch 7
PITCH, R, RED_R, STOP = 7.0, 2.6, 3.0, 5.6
white, stop = dots(108, PITCH, R, RED_R, STOP)
(res / "drawable" / "ic_launcher_foreground.xml").write_text(
    vector(108, [(WHITE, R, white), (RED, RED_R, [stop])])
)
# Themed icon: one colour, the system tints it
(res / "drawable" / "ic_launcher_monochrome.xml").write_text(
    vector(108, [(WHITE, R, white + []), (WHITE, RED_R, [stop])])
)
# Notification: 24 viewport, white only (status bar icons are masks)
nw, ns = dots(24, 2.6, 1.0, 1.15, 5.6)
(res / "drawable" / "ic_notification.xml").write_text(vector(24, [(WHITE, 1.0, nw), (WHITE, 1.15, [ns])]))

# SVG preview on black, with the safe-zone circle for reference
svg = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">',
       '<rect width="108" height="108" fill="#000"/>',
       '<circle cx="54" cy="54" r="33" fill="none" stroke="#333" stroke-width="0.3"/>']
svg += [f'<circle cx="{x:.2f}" cy="{y:.2f}" r="{R}" fill="#fff"/>' for x, y in white]
svg.append(f'<circle cx="{stop[0]:.2f}" cy="{stop[1]:.2f}" r="{RED_R}" fill="#D71921"/>')
svg.append("</svg>")
(preview_dir / "logo.svg").write_text("\n".join(svg))
print("ok", len(white), "dots")
