# eBirja avtotestlari: ro'yxat va so'nggi natijalar

Oxirgi yangilanish: 23.11.2026, 22:20. Barcha testlar test saytida (`test-app.ebirja.uz`, `test-head.ebirja.uz`) E-IMZO kaliti bilan ishlaydi.

## Qanday ishga tushiriladi

IntelliJ'da yuqoridagi ro'yxatdan tugmani tanlab ▶ bosiladi. Test vaqtida klaviatura va sichqonchaga tegmang, chunki E-IMZO paroli avtomatik kiritiladi.

| Tugma | Nima qiladi |
|---|---|
| **To'liq jarayon** | Hammasi bitta run'da: kontrakt salbiy testlari → auksion (7 bitim) → 7 ta bitim holati |
| Auksion - Oddiy | Faqat auksion |
| Kontrakt - negativ | Faqat kontrakt formasidagi salbiy holatlar |
| Bitim holatlari | Faqat 7 ta stsenariy (bitimlar `-Dbitim.<kalit>=B...` bilan beriladi) |
| Auksion - Spirt / Oltin / Kumush / Subsidiya | Maxsus savdolar (keyingi bosqich, hali o'ziga xos qadamlari yo'q) |

Terminaldan: `mvn test -Dsuite=toliq-jarayon.xml`

Sozlamalar (`login.properties`): `auction.lots=100`, `auction.bids=7`, `auction.bidPauseMs=2000`, `auction.minutes=1` (e'lon 1 daq, savdo 1 daq), `auction.buyerClient=313141918` (TESTIVEST MCHJ).

## Tezlik

| Run | Vaqt | Izoh |
|---|---|---|
| 23.11, 21:57 (to'liq jarayon) | **17 daq** | Eski usullar: har taklifga oyna alohida ochilgan, admin bittalab tasdiqlagan, E-IMZO sekin kuzatilgan |

Shu run'dan keyin quyidagi tezlashtirishlar qilindi. Ular **kompilyatsiya qilingan, lekin to'liq run'da hali sinalmagan** (24.11 da birinchi tekshiriladi):
- E-IMZO oynasini kuzatuvchi doimiy jarayon (`eimzo-server.ps1`): bitta imzo ~6,7 s → ~2–3 s kutilmoqda;
- 7 ta taklif oynasi savdodan **oldin** bitta sahifada ochib qo'yiladi, keyin 2 s oraliq bilan yuboriladi;
- treyderlar «Bitim holati = Imzolanish kutilmoqda» filtri + «hammasini tanlash» bilan ommaviy imzolaydi;
- admin «Tasdiqlanmagan» tabida «hammasini tanlash» + ommaviy tasdiqlaydi.

Maqsad: **10–13 daqiqa**.

## 1. Kontrakt yaratish: salbiy holatlar

Natija: **13 tadan 12 tasi o'tdi, 1 ta xato topildi**. Imzo so'rovi brauzerda to'sib qo'yiladi, hech narsa yuborilmaydi.

| Holat | Natija |
|---|---|
| Narx, lot hajmi, ikkala zakalat, yetkazib berish va to'lov muddatiga minus (-500) | ✅ minus kiritilmaydi |
| **Narx = 0** | ❌ **XATO: forma o'tadi va imzo so'raydi** |
| Sotuvchi / xaridor zakalati 0% | ✅ rad etiladi: «0,1 % – 10 % oralig'ida bo'lishi kerak» |
| Sotuvchi / xaridor zakalati 15% | ✅ maydon 10% dan ortig'ini kiritmaydi |
| Bo'sh forma | ✅ rad etiladi: «Majburiy maydon» |
| Hujjat yuklanmagan | ✅ rad etiladi: «kamida 1 ta fayl kiritilishi kerak» |

## 2. Auksion (`AuksionOddiyTest`)

| # | Qadam | Holat |
|---|---|---|
| 1 | Treyder 1 kontrakt yaratadi | ✅ |
| 2 | Moderator 3 rolda tasdiqlaydi | ✅ |
| 3 | Admin savdo vaqtini qo'yadi (e'lon 1 daq, savdo 1 daq) | ✅ |
| 4 | 100 lot savdoga; salbiy: **-5 lot** (minus kiritilmaydi), **0 lot** (rad: «Grafik lot yuborilishi kerak») | ✅ |
| 5 | Treyder 2 TESTIVEST nomidan 1 lotdan 7 ta taklif | ⚠️ 21:57 run'da 7-taklif savdo tugagach yetib bordi (6 bitim). Tuzatildi (yuqoriga qarang) |
| 6 | Bitimlar hosil bo'ladi | ✅ |
| 7 | Ikkala treyder ommaviy imzolaydi | ✅ |
| 8 | Admin tasdiqlaydi | ✅ |
| 9 | Salbiy holatlar yakuni | ✅ |

Qo'shilgan, hali sinalmagan (5-qadam, savdoning qolgan vaqtida): **0 lotga taklif** (rad etilishi kerak), **101 lotga taklif** (qabul qilinadi, qisman yutadi, savatchada sariq), **sotuvchi o'z mahsulotiga taklif** (rad etilishi kerak). Savdodan keyin «Mening takliflarim» (savatcha) ranglari o'qiladi: yashil — yutgan, qizil — yutqizgan, sariq — qisman.

## 3. Bitim holatlari (`BitimHolatlariTest`): 7 ta stsenariy

Har bir stsenariy o'z bitimida. Balans va jarima HKKP → Balans dan tekshiriladi.

| # | Stsenariy | Bitim | Natija | Tekshirilgani |
|---|---|---|---|---|
| 1 | To'lov → YBHR to'liq → xaridor tasdiqlaydi | B26000263 | ✅ | To'lovda TESTIVEST erkin mablag'i **900 000** ga kamaydi; holat **«Bajarildi»** |
| 2 | To'lov → YBHR yarmi (0,5) → kelishuv bilan bekor | B26000264 | ✅ | Holat **«Qisman bajarildi»**, jarima yo'q |
| 3 | Kelishuv bilan bekor qilish | B26000265 | ✅ | Jarima yo'q, zakalat (100 000) blokdan chiqdi |
| 4 | Xaridor jarimasi | B26000266 | ✅ | **100 000** (xaridor zakalati) TESTIVEST → sotuvchi |
| 5 | Sotuvchi jarimasi | B26000267 | ✅ | **100 000** (sotuvchi zakalati) sotuvchi → TESTIVEST |
| 6 | To'lov muddatini uzaytirish: qabul va rad | B26000268 | ✅ | Qabul: 26.11 → **30.11** (aynan 2 ish kuni); rad: sana o'zgarmadi |
| 7 | Yetkazib berish muddatini uzaytirish: qabul va rad | B26000262 | ✅ | Qabul: 07.12 → **09.12** (2 ish kuni); rad: sana o'zgarmadi |

YBHR tartibi: shartnoma raqami → faktura raqami, sana, miqdor → **xlsx hujjat yuklanadi** → «Ro'yxatga qo'shish» → «Yuborish» → xaridor YBHR sahifasida tasdiqlaydi.

## 4. Topilgan xatolar va savollar

| # | Joy | Nima | Holat |
|---|---|---|---|
| 1 | Kontrakt yaratish | **Narx 0 bo'lsa ham forma o'tadi va imzo so'raydi** | ❌ Xato |
| 2 | Birja komissiyasi | Har tomondan **800 so'm = 0,08%** (1 000 000 dan), bitim tuzilganda yechiladi. Kutilgan 0,12% | ❓ Aniqlash kerak |
| 3 | Kontrakt formasi | «0,1 % – 10 % oralig'ida bo'lishi kerak» yozuvi to'g'ri qiymatda ham ko'rinadi | ❓ Avvalroq kuzatilgan |

Aniqlangan qoida (xato emas): mavjuddan ko'p lotga (101) taklif qabul qilinadi va bor lot bilan qisman bajariladi (23.11 da B26000255: 100 lot).

## 5. Keyingi qadamlar (24.11)

1. To'liq jarayonni yangi tezlashtirishlar bilan ishga tushirish va vaqtni o'lchash (maqsad 10–13 daq).
2. Savdodagi yangi salbiy holatlar va savatcha ranglarini tekshirish.
3. Komissiya 0,12% / 0,08% ni aniqlab, testga tekshiruv qo'shish.
4. Keyin maxsus savdolar: Spirt, Oltin (Tilla), Kumush, Subsidiya — har biri alohida run.
