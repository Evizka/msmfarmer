#!/usr/bin/env python3
"""Offline evaluation of the proposed colour-only BagDetector, NOT production code.

Usage: python tools/evaluate_bag_proposal.py /path/to/full-screen.png
Requires: pip install numpy opencv-python-headless
The input remains local. The output lists candidate centres in original pixels.
The calculation mirrors the proposed Kotlin thresholds, including its full-frame
scan and cross-type NMS; it intentionally does not press or modify anything.
"""
import numpy as np, cv2, sys
if len(sys.argv) != 2:
    raise SystemExit('Usage: python tools/evaluate_bag_proposal.py /path/to/full-screen.png')
p = sys.argv[1]
raw = cv2.imread(p)
if raw is None:
    raise SystemExit(f'Cannot read image: {p}')
img = raw[:, :, ::-1]
del raw
h, w = img.shape[:2]
print('frame', p, w, h, flush=True)
print('Kotlin integral tables alone:', round(8 * (w + 1) * (h + 1) * 4 / 1024**2, 1), 'MiB')
r,g,b=(img[:,:,i].astype(np.float32) for i in range(3)); mx=np.maximum(np.maximum(r,g),b); mn=np.minimum(np.minimum(r,g),b); d=mx-mn; dd=np.where(d==0,1,d); sat=d/np.where(mx==0,1,mx)
hue=np.where(mx==r, np.mod((g-b)/dd,6), np.where(mx==g,(b-r)/dd+2,(r-g)/dd+4));hue=np.mod(hue*60+360,360); cl=np.zeros((h,w),dtype=np.uint8)
def assign(mask,k): cl[(cl==0)&(mx>=35)&mask]=k
assign((sat<.30)&(mx>=70)&(mx<=215),1)
assign((hue>=20)&(hue<=55)&(sat<.42)&(mx>130),2)
assign((hue>=35)&(hue<=62)&(sat>=.42)&(mx>120),3)
assign((hue>=255)&(hue<=330)&(sat>=.25)&(mx>70),4)
assign((hue>=145)&(hue<=195)&(sat>=.20)&(mx>90),6)
assign((hue>=75)&(hue<=145)&(sat>=.30)&(mx>70),5)
assign((hue>=5)&(hue<=35)&(sat>=.35)&(mx>=50)&(mx<=180),7)
del r,g,b,mx,mn,d,dd,hue,sat,img
integrals=[None]+[cv2.integral(np.uint8(cl==k),sdepth=cv2.CV_32S) for k in range(1,8)]
print('memory integral',round(sum(a.nbytes for a in integrals[1:])/1024**2,1),'MiB',flush=True)
out=[]
for base in [64,80,96,112,128,148,168,192,220]:
 s=int(base*h/1080+.5); inset=int(s*.18); cs=int(s*.32);step=max(2,s//12)
 if s<28 or s>=w or s>=h: continue
 xx=np.arange(0,w-s+1,step,dtype=np.int32)[None,:]; yy=np.arange(0,h-s+1,step,dtype=np.int32)[:,None];
 def rect(k,x0,y0,x1,y1):
  a=integrals[k];return a[y1,x1]-a[y0,x1]-a[y1,x0]+a[y0,x0]
 stone=(rect(1,xx,yy,xx+s,yy+s)-rect(1,xx+inset,yy+inset,xx+s-inset,yy+s-inset))/(s*s-(s-2*inset)**2)
 cx0=xx+cs;cy0=yy+cs;cx1=xx+s-cs;cy1=yy+s-cs;area=(s-2*cs)**2
 bag=rect(2,cx0,cy0,cx1,cy1)/area;gold=rect(3,cx0,cy0,cx1,cy1)/area;purple=rect(4,cx0,cy0,cx1,cy1)/area;gem=rect(6,cx0,cy0,cx1,cy1)/area;wood=rect(7,cx0,cy0,cx1,cy1)/area
 for kind,mask,score in [('GOLD',(stone>=.4)&(bag>=.16)&(gold>=.06)&(gold>=purple),bag+gold),('PURPLE',(stone>=.4)&(bag>=.16)&(purple>=.06)&(purple>gold),bag+purple),('MINE',(stone>=.4)&(bag<.10)&(gem>=.07)&(wood>=.05),gem+wood)]:
  ys,xs=np.where(mask)
  for a,b in zip(ys,xs):out.append((float(score[a,b]),int(xx[0,b]+s/2),int(yy[a,0]+s/2),s,kind))
def suppress(items):
    kept = []
    for score, x, y, s, kind in sorted(items, reverse=True):
        if all((px-x)**2 + (py-y)**2 >= (0.5 * max(ps, s))**2 for _, px, py, ps, _ in kept):
            kept.append((score, x, y, s, kind))
    return kept

for mode, items in [('coins (wantMine=false)', [v for v in out if v[4] != 'MINE']),
                    ('coins and mines (wantMine=true)', out)]:
    kept = suppress(items)
    print(mode, 'raw', len(items), 'nms', len(kept),
          'types', {k: sum(q[-1] == k for q in kept) for k in ('GOLD', 'PURPLE', 'MINE')})
    print('hits', [(round(a, 3), x, y, size, k) for a, x, y, size, k in kept[:40]])
