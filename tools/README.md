# Offline colour-detector evaluation

`evaluate_bag_proposal.py` is an offline Python reproduction of the proposed
colour-only `BagDetector` (it is not used by the Android application).
Install `numpy` and `opencv-python-headless` in a virtual environment, then run:

```sh
python tools/evaluate_bag_proposal.py /path/to/full-resolution-game-screenshot.png
```

The script keeps the image local, prints candidate coordinates and evaluates
both with and without mine detection. It uses the proposed full-screen scan,
thresholds and cross-type non-maximum suppression. It is a Python reproduction,
not an on-device benchmark or proof of bit-exact Kotlin behaviour.

Observed on the provided 2400×1080 screenshots, using `wantMine=false`:

* `11381.png` (island map): 11 gold-bag candidates, although no gold bags are
  present in the scene. For example `(1412, 872)` and `(2332, 272)`.
* `11380.png` (snow island): 10 gold and one purple candidate, including
  candidates over UI/decor; visible gold bag centres near `(1048, 200)` and
  `(1296, 296)` were not found at their expected locations.
* `11382.png` (purple island): 35 gold and six purple candidates despite the
  visible collectible bags being purple.

The proposed eight full-resolution integral images alone require about 79.2 MiB
for a 2400×1080 frame, excluding other buffers, the screenshot and app memory.
An optimisation or stronger classifier must still be tested on-device. The
original screenshots are intentionally not included in the repository.
