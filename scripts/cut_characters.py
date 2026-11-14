"""Cuts art/characters-sheet.png (twelve round characters on black, six to a row, in two rows) into the app's
character pictures: res/drawable-nodpi/av_<name>.png. Each is cut exactly to its circle (found by measuring the
sheet: every circle is 234 px across) and nothing more; Cinema shows each in a circle of its own.
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
NAMES = ["popcorn", "reel", "clapper", "ghost", "alien", "robot", "vampire", "cowboy", "astronaut", "cat", "monster", "director"]
DIAMETER = 234
# Each circle's centre on the sheet, in the order above.
CENTRES = [(138.5, 288), (390.5, 288), (640, 287), (889, 288), (1138, 288), (1393.5, 288),
           (138.5, 664), (391.5, 664), (640, 663), (888.5, 664), (1138.5, 664), (1393.5, 664)]

sheet = Image.open(os.path.join(HERE, "..", "art", "characters-sheet.png")).convert("RGB")
out = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable-nodpi")
r = DIAMETER / 2
for name, (cx, cy) in zip(NAMES, CENTRES):
    box = (round(cx - r), round(cy - r), round(cx - r) + DIAMETER, round(cy - r) + DIAMETER)
    sheet.crop(box).save(os.path.join(out, f"av_{name}.png"), optimize=True)
    print("wrote", name)
