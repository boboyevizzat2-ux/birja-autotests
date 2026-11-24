# eBirja avtotestlari: ro'yxat va so'nggi natijalar

Oxirgi yangilanish: 24.11.2026, 21:00. Testlar hozir `test-app.ebirja.uz` (treyder) va `test-head.ebirja.uz` (admin) da ishlaydi (`login.properties` dagi 2 qator; mikroservis versiyasi: test-ms-app / test-ms-head) E-IMZO kaliti bilan ishlaydi.

## Qanday ishga tushiriladi

IntelliJ'da yuqoridagi ro'yxatdan tugmani tanlab ▶ bosiladi. Test vaqtida klaviatura va sichqonchaga tegmang, chunki E-IMZO paroli avtomatik kiritiladi.

| Tugma | Nima qiladi |
|---|---|
| **To'liq jarayon** | Hammasi bitta run'da: kontrakt salbiy testlari → auksion (7 bitim) → 7 ta bitim holati |
| Auksion - Oddiy | Faqat auksion |
| **Qarama-qarshi auksion** | Kontrakt (moderator) → sessiya → savdo (salbiy + 7 bitim) → imzolash, tasdiqlash → 7 ta bitim holati |
| Kontrakt - negativ | Faqat kontrakt formasidagi salbiy holatlar |
| Bitim holatlari | Faqat 7 ta stsenariy (bitimlar `-Dbitim.<kalit>=B...` bilan beriladi) |
| Auksion - Spirt / Oltin / Kumush / Subsidiya | Maxsus savdolar (keyingi bosqich, hali o'ziga xos qadamlari yo'q) |

Terminaldan: `mvn test -Dsuite=toliq-jarayon.xml`

Sozlamalar (`login.properties`): `auction.lots=100`, `auction.bids=7`, `auction.bidPauseMs=2000`, `auction.minutes=1` (e'lon 1 daq), `auction.savdoMinutes=2` (savdo 2 daq), `auction.buyerClient=313141918` (TESTIVEST MCHJ).

## Tezlik

| Run | Vaqt | Natija |
|---|---|---|
| 23.11, 21:57 | 17 daq | Eski usullar (har taklifga alohida oyna, admin bittalab tasdiqlagan) |
| 24.11, 14:43 (test-ms-app) | 14 daq 33 s | 30 testdan 29 tasi; «Narx = 0» testi noto'g'ri tekshirgan edi (keyin tuzatildi) |
| **24.11, 16:57 (test-app, monolit)** | **14 daq 4 s** | **30 testdan 29 tasi**; «Narx = 0» testi tuzatildi va alohida o'tdi → 30/30 |

Qilingan tezlashtirishlar:
- E-IMZO oynasini doimiy kuzatuvchi jarayon (`eimzo-server.ps1`);
- savdo vaqtini admin o'zi yaratadi: eski qatorlar o'chiriladi, mavjud sessiya kutilmaydi;
- moderator bitta brauzerda rol almashtiradi;
- 7 ta taklif oynasi savdodan oldin bitta sahifada ochiladi, keyin 2 s oraliq bilan yuboriladi;
- treyderlar «Imzolanish kutilmoqda» filtri + «hammasini tanlash» bilan ommaviy imzolaydi;
- admin «Tasdiqlanmagan» tabida ommaviy tasdiqlaydi;
- har bir foydalanuvchi uchun doimiy brauzer sessiyasi (qayta kirish yo'q).

24.11 run'i bo'yicha vaqt:

| Qadam | Vaqt |
|---|---|
| Kontrakt salbiy testlari | 1:46 |
| 1. Kontrakt yaratish | 0:18 |
| 2. Moderator (3 rol) | 0:32 |
| 3. Savdo vaqtini yaratish (eski qatorlarni o'chirish + 2 ta yangi) | 0:50 |
| 4. Savdoga chiqarish (e'lon boshlanishini kutish bilan) | 1:10 |
| 5. Takliflar (savdo boshlanishini kutish bilan; 7 ta oyna 8,9 s, 7 ta taklif 46 s) | 1:29 |
| 6. Savdo tugashi va bitimlarni yig'ish | 1:30 |
| 7. Ikki treyder ommaviy imzolashi | 0:38 |
| 8. Admin ommaviy tasdiqlashi | 0:16 |
| Bitim stsenariylari 1–7 | 1:19 / 1:18 / 0:30 / 0:31 / 0:31 / 0:50 / 1:17 |

4–6-qadamlarning katta qismi savdo jadvalini kutish (e'lon 1 daq, savdo 2 daq). Savdo 2 daqiqa qilingan, chunki E-IMZO har imzoga ~6,6 s sarflaydi: 7 ta taklif (46 s) va 101 / 0 lotli tekshiruvlar 1 daqiqaga sig'maydi.

## 1. Kontrakt yaratish: salbiy holatlar

Natija: **13 tadan 13 tasi o'tdi**. Imzo so'rovi brauzerda to'sib qo'yiladi, hech narsa yuborilmaydi. Faqat «Narx = 0» da parol kiritiladi, chunki sayt narxni imzodan keyin serverda tekshiradi.

| Holat | Natija |
|---|---|
| Narx, lot hajmi, ikkala zakalat, yetkazib berish va to'lov muddatiga minus (-500) | ✅ minus kiritilmaydi |
| Narx = 0 | ✅ imzodan keyin server rad etadi: «Narx qiymati musbat bo'lishi kerak» |
| Sotuvchi / xaridor zakalati 0% | ✅ rad etiladi: «0,1 % – 10 % oralig'ida bo'lishi kerak» |
| Sotuvchi / xaridor zakalati 15% | ✅ maydon 10% dan ortig'ini kiritmaydi |
| Bo'sh forma | ✅ rad etiladi: «Majburiy maydon» |
| Hujjat yuklanmagan | ✅ rad etiladi: «kamida 1 ta fayl kiritilishi kerak» |

## 2. Auksion (`AuksionOddiyTest`)

| # | Qadam | Holat |
|---|---|---|
| 1 | Treyder 1 kontrakt yaratadi | ✅ |
| 2 | Moderator 3 rolda tasdiqlaydi | ✅ |
| 3 | Admin savdo vaqtini o'zi yaratadi (e'lon 1 daq, savdo 2 daq) | ✅ |
| 4 | 100 lot savdoga; salbiy: **-5 lot** (minus kiritilmaydi), **0 lot** (rad: «Grafik lot yuborilishi kerak») | ✅ |
| 5 | Treyder 2 TESTIVEST nomidan 1 lotdan 7 ta taklif (bitta sahifada 7 ta oyna, 2 s oraliq) | ✅ |
| 6 | Bitimlar hosil bo'ladi (B26000302–B26000308, 1 lotdan) | ✅ |
| 7 | Ikkala treyder ommaviy imzolaydi | ✅ |
| 8 | Admin ommaviy tasdiqlaydi | ✅ |
| 9 | Salbiy holatlar yakuni | ✅ |

Savdo vaqtidagi salbiy va chegaraviy holatlar:

| Holat | Natija |
|---|---|
| Sotuvchi o'z mahsulotiga taklif | ✅ mahsulot sotuvchiga ko'rinmaydi, taklif bera olmaydi |
| 0 lotga taklif | ✅ rad etiladi: «Min 1» |
| 101 lotga taklif (savdoda 100 lot, 7 tasi sotilgan) | ✅ qabul qilinadi va qolgan **93 lotni** yutadi (B26000309) |

Bitimlar faqat shu savdo vaqtida tuzilganlardan olinadi, oldingi run'lardan qolgan imzolanmagan bitimlar aralashmaydi. «Mening takliflarim» (savatcha) ranglarini o'qish hali ishlamaydi.

## 3. Bitim holatlari (`BitimHolatlariTest`): 7 ta stsenariy

Har bir stsenariy o'z bitimida (24.11 run'i). Balans va jarima HKKP → Balans dan tekshiriladi.

| # | Stsenariy | Bitim | Natija | Tekshirilgani |
|---|---|---|---|---|
| 1 | To'lov → YBHR to'liq → xaridor tasdiqlaydi | B26000302 | ✅ | To'lovda TESTIVEST erkin mablag'i **900 000** ga kamaydi; holat **«Bajarildi»** |
| 2 | To'lov → YBHR yarmi (0,5) → kelishuv bilan bekor | B26000303 | ✅ | Holat **«Qisman bajarildi»**, jarima yo'q |
| 3 | Kelishuv bilan bekor qilish | B26000304 | ✅ | Jarima yo'q, zakalat (100 000) blokdan chiqdi |
| 4 | Xaridor jarimasi | B26000305 | ✅ | **100 000** (xaridor zakalati) TESTIVEST → sotuvchi |
| 5 | Sotuvchi jarimasi | B26000306 | ✅ | **100 000** (sotuvchi zakalati) sotuvchi → TESTIVEST |
| 6 | To'lov muddatini uzaytirish: qabul va rad | B26000307 | ✅ | Qabul: 27.11 → **01.12** (aynan 2 ish kuni); rad: sana o'zgarmadi |
| 7 | Yetkazib berish muddatini uzaytirish: qabul va rad | B26000308 | ✅ | Qabul: 08.12 → **10.12** (2 ish kuni); rad: sana o'zgarmadi |

YBHR tartibi: shartnoma raqami → faktura raqami, sana, miqdor → **xlsx hujjat yuklanadi** → «Ro'yxatga qo'shish» → «Yuborish» → xaridor YBHR sahifasida tasdiqlaydi.

## 4. Qarama-qarshi auksion (`QaramaQarshiAuksionTest`) — ishlanmoqda

Ishga tushirish: IntelliJ **«Qarama-qarshi auksion»** tugmasi yoki `mvn test -Dsuite=qarama-qarshi.xml`. Oddiy auksiondan alohida run.

Jarayon (24.11 da qo'lda o'tib aniqlandi):
1. Moderator **LISTING_MAIN** rolida kontrakt yaratadi (Listing → Qaramaqarshi auksion kontrakt ro'yxati → Kontrakt yaratish), **LISTING** roli tasdiqlaydi → «Ochilgan kontraktlar», savdo raqami (Kontrakt №) beriladi.
2. Admin: Qarama qarshi auksion savdo sessiyasi → «Savdo sessiyasi» → sessiya turi, boshlanish/tugash vaqti (test: hozirdan 30 daqiqa). E'lon vaqti yo'q.
3. Treyder: Savdolar → Qarama qarshi auksion savdolari → kartochkadagi bolg'acha → mijoz, miqdor, narx → «Sotish» / «Sotib olish». E-IMZO so'ralmaydi. Mijoz balansi yuklanmaguncha tugmalar o'chiq turadi.
4. **Narx bir xil bo'lsa** bitim darhol tuziladi (savdo tugashini kutmaydi), raqami `A2600....`. Miqdor yetmasa — bor lot bilan qisman bajariladi, qolgani ochiq buyurtma bo'lib qoladi. Ochiq buyurtma «Ochiq buyurtmalar» oynasida belgilab «O'chirish» bilan bekor qilinadi.
5. Keyin oddiy auksiondagidek: ikki treyder ommaviy imzolaydi, admin tasdiqlaydi, 7 ta stsenariy.

24.11, 20:18 dagi run (19 daq 19 s) natijasi:

| Qadam | Vaqt | Natija |
|---|---|---|
| Kontrakt formasi: minus qiymatlar, zakalat 0% / 15%, bo'sh forma, narx 0 | 1:35 | ✅ rad etildi |
| Kontrakt formasi: hujjatsiz | — | ❌ **sayt xatosi**: kontrakt yaratildi («So'rov yuborildi!») |
| Kontrakt yaratish + LISTING tasdiqlashi + sessiya | 0:57 | ✅ |
| Savdo: minus miqdor/narx (maydon «-» ni olib tashlaydi), 0 miqdor («Min 1»), 0 narx («musbat bo'lishi kerak»), past narx, o'zidan sotib olish | 4:14 | ✅ bitim tuzilmadi |
| Savdo: 7 ta bitim (A26000334–340), qisman bajarilish (2 lot bor, 3 so'raldi → A26000341, 1 lot ochiq qoldi) | 5:04 | ✅ |
| Ommaviy imzolash + admin tasdiqlash | 0:58 | ✅ |
| 7 ta stsenariy (to'lov+YBHR, yarim YBHR+kelishuv, kelishuv, xaridor/sotuvchi jarimasi, to'lov/yetkazish muddati) | 6:00 | ✅ hammasi |

Shundan keyingi o'zgarishlar (25.11 da sinaladi): balans HKKP dan o'qiladi (har bitimda 100 000 zakalat + 800 yig'im), yuqori narxda bitim **tuzilmasligi** kutiladi (qoida: narx bir xil bo'lishi kerak), tezlashtirish (bitimlar ochiq buyurtmalar orqali tekshiriladi). Oxirgi run'da buyurtma oynasi ochilmay qoldi — bolg'achani qayta bosish qo'shildi, sinalmagan.

## 5. Topilgan xatolar va savollar

| # | Joy | Nima | Holat |
|---|---|---|---|
| 1 | Birja komissiyasi | Har tomondan **800 so'm = 0,08%** (1 000 000 dan), bitim tuzilganda yechiladi | ✅ To'g'ri (0,08% tasdiqlandi) |
| 2 | Kontrakt formasi | «0,1 % – 10 % oralig'ida bo'lishi kerak» yozuvi to'g'ri qiymatda ham ko'rinadi | ❓ Avvalroq kuzatilgan |
| 3 | Qarama-qarshi auksion kontrakti (moderator) | **Hujjat yuklanmasa ham kontrakt yaratiladi** (treyder formasida rad etiladi) | ❌ Xato |

Aniqlangan qoida (xato emas): mavjuddan ko'p lotga (101) taklif qabul qilinadi va qolgan lot bilan qisman bajariladi.

## 6. Keyingi qadamlar (25.11)

1. Qarama-qarshi auksion: to'liq run'ni o'tkazish, balans tekshiruvini tasdiqlash.
2. Qarama-qarshi auksionga qo'shimcha salbiy holatlar: mijozsiz / bo'sh / kasr / harfli qiymatlar, mablag'dan katta xarid, «Bozor narxida» xarid, tugmani ikki marta bosish, sessiya tugagach buyurtma, bekor qilingan buyurtmaga mos taklif, kontraktni rad etish, noto'g'ri fayl turi, muddatlar 0, bitta tomon imzolagan bitim, ochiq buyurtmada pul bloklanishi va qaytishi.
3. Savatcha («Mening takliflarim») ranglarini tekshirish: yashil — yutgan, qizil — yutqizgan, sariq — qisman.
4. Maxsus savdolar: Spirt, Oltin (Tilla), Kumush, Subsidiya — har biri alohida run.
