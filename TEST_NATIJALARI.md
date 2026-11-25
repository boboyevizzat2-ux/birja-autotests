# eBirja avtotestlari: ro'yxat va so'nggi natijalar

Oxirgi yangilanish: 25.11.2026, 11:30. Testlar hozir **ms** da ishlaydi: `test-ms-app.ebirja.uz` (treyder), `test-ms-head.ebirja.uz` (admin), E-IMZO kaliti bilan. Eski saytga (`test-app` / `test-head`) o'tish uchun `login.properties` dagi `trader.url` va `admin.url` almashtiriladi, kod o'zgarmaydi.

**Joriy holat:** oxirgi run (25.11, 11:23, ms) oxirigacha bormadi: moderator (admin1) kira olmadi, server `400 /api/erp, state 1259` qaytardi. Oxirigacha borgan oxirgi run 25.11, 11:01 (pastdagi jadval).

## Qanday ishga tushiriladi

IntelliJ'da yuqoridagi ro'yxatdan tugmani tanlab ▶ bosiladi. Test vaqtida klaviatura va sichqonchaga tegmang, chunki E-IMZO paroli avtomatik kiritiladi.

| Tugma | Nima qiladi |
|---|---|
| **To'liq jarayon** | Hammasi bitta run'da: kontrakt salbiy testlari → oddiy auksion (8 kontrakt) → bitim holatlari |
| Auksion - Oddiy | Faqat auksion |
| Kontrakt - negativ | Faqat kontrakt formasidagi salbiy holatlar (to'liq jarayon ichida ham bor) |
| Bitim holatlari | Faqat bitim stsenariylari (bitimlar `-Dbitim.<kalit>=B...` bilan beriladi) |
| Qarama-qarshi auksion | Alohida run, ishlanmoqda (4-bo'lim) |
| Auksion - Spirt / Oltin / Kumush / Subsidiya | Maxsus savdolar (keyingi bosqich, hali o'ziga xos qadamlari yo'q) |

Terminaldan: `mvn test -Dsuite=toliq-jarayon.xml`

Sozlamalar (`login.properties`): `auction.lots=100`, `auction.bids=9` (9 ta taklif), `auction.deleteBids=1` (bittasi o'chiriladi, 8 ta kontrakt qoladi), `auction.bidPauseMs=2000`, `auction.minutes=1` (e'lon), `auction.savdoMinutes=3` (savdo), `auction.buyerClient=313141918` (TESTIVEST MCHJ).

## Run'lar

Har run'da `operatsiyalarniKorish` ataylab o'tkazib yuboriladi (faqat qo'lda, ma'lumot ko'rsatadi).

| Run | Sayt | Vaqt | O'tdi / xato / o'tkazildi | Izoh |
|---|---|---|---|---|
| 23.11, 21:57 | app | 17 daq | | Eski usullar |
| 24.11, 14:43 | ms | 14:33 | 28 / 1 / 1 | Xato: «Narx = 0» testi noto'g'ri tekshirgan (test xatosi) |
| 24.11, 16:57 | app | 14:04 | 28 / 1 / 1 | O'sha xato |
| 25.11, 09:38 | app | 14:10 | 28 / 1 / 1 | YBHR yuborishda E-IMZO paroli noto'g'ri tushgan; endi bir marta qayta yuboriladi |
| 25.11, 09:53 | app | 14:38 | 28 / 2 / 1 | Taklifni o'chirish tugmasi topilmagan (test xatosi, tuzatildi) |
| 25.11, 10:08 | app | 5:56 | 10 / 0 / 0 | Faqat auksion |
| 25.11, 10:24 | ms | 14:59 | 27 / 2 / 2 | 1-taklif savdo ochilmasdan berilgan (test xatosi); 7-stsenariyga bitim yetmagan |
| **25.11, 11:01** | **ms** | **17:31** | **29 / 3 / 1** | Uchala xato sayt tomonda (5-bo'lim: 1, 2, 4) |
| 25.11, 11:23 | ms | 2:54 | 14 / 1 / 18 | admin1 kira olmadi (1259), auksion boshlanmadi |

Avvalgi tahrirda «30 testdan 29 tasi» va «30/30» deb yozilgan edi, bu noto'g'ri: o'tkazib yuborilgan test o'tgan deb sanalgan.

11:01 dagi run bo'yicha vaqt:

| Qadam | Vaqt |
|---|---|
| Kontrakt salbiy testlari | ~2:20 |
| 1. Kontrakt yaratish | 0:19 |
| 2. Moderator (3 rol) | 0:31 |
| 3. Savdo vaqtini yaratish | 0:37 |
| 4. Savdoga chiqarish | 0:45 |
| 5. Takliflar, savatcha, salbiy holatlar | 1:25 |
| 6. Savdo tugashini kutish va bitimlarni yig'ish | 2:47 |
| 7. Ikki treyder ommaviy imzolashi | 0:58 |
| 8. Admin ommaviy tasdiqlashi | 0:16 |
| Stsenariylar h1–h8 | 1:18 / 1:30 / 0:45 (xato) / 0:42 / 0:41 / 0:55 / 1:23 / 0:18 |

Savdo 3 daqiqa, undan taxminan 2 daqiqasi ortib qoladi va kutiladi. Taklif berishda **E-IMZO so'ralmaydi**. Avval bu yerda «E-IMZO har imzoga 6,6 s sarflaydi» deb yozilgan edi, bu noto'g'ri: test chiqmaydigan oynani kutgan. Endi 9 ta taklif 17 s.

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

## 2. Oddiy auksion (`AuksionOddiyTest`), 25.11, 11:01, ms

| # | Qadam | Holat |
|---|---|---|
| 1 | Treyder 1 kontrakt yaratadi | ✅ |
| 2 | Moderator 3 rolda tasdiqlaydi | ✅ (kirish goh ishlamaydi, 5-bo'lim) |
| 3 | Admin savdo vaqtini o'zi yaratadi (e'lon 1 daq, savdo 3 daq) | ✅ |
| 4 | 100 lot savdoga; salbiy: **-5 lot** (minus kiritilmaydi), **0 lot** («Grafik lot yuborilishi kerak») | ✅ |
| 5 | Treyder 2 TESTIVEST nomidan 1 lotdan 9 ta taklif, bittasi 🗑 bilan o'chiriladi | ✅ o'chirilganidan bitim chiqmadi |
| 6 | 8 ta kontrakt (B26000382–389) | ✅ |
| 7 | 101 lotga taklif (100 lot, 8 tasi sotilgan) | ✅ qolgan **92 lotni** oldi (B26000390) |
| 8 | Ikkala treyder ommaviy imzolaydi, admin ommaviy tasdiqlaydi | ✅ |

**Savatcha** (kartochkadagi bolg'acha yonidagi belgi), savdo ichida o'qiladi: yashil — yutgan (8), sariq — qisman (101 lot), qizil — yutqazgan (101 dan keyingi 1 lot). ✅

**Savdo ichidagi salbiy holatlar.** Hukm «Mening takliflarim» dagi qatorlar soniga qarab: noto'g'ri taklif ro'yxatga tushsa, sayt qabul qilgan hisoblanadi va taklif darhol o'chiriladi.

| Holat | Natija |
|---|---|
| Minus lot, minus pul, kasr lot, harf lot, harf pul | ✅ maydon qabul qilmaydi (kasr 1.5 → 15 bo'lib qoladi) |
| 0 lot / 0 pul | ✅ «Min 1» / «Narx qiymati musbat bo'lishi kerak» |
| Bo'sh lot / bo'sh pul / mijoz tanlanmagan | ✅ «Majburiy maydon» |
| Past narx (900 000) | ✅ taklif berish mumkin, savatchada ko'rinmaydi, kontrakt tuzilmadi |
| Sotuvchi o'z mahsulotiga | ✅ mahsulot sotuvchiga ko'rinmaydi |
| Treyder o'z kompaniyasi nomidan | qabul qilinadi (ma'lumot uchun, o'chiriladi) |
| **999 999 999 lot** (mablag'dan ancha katta) | ❌ **qabul qilindi** |
| Savdo tugagandan keyin | ❓ aniqlanmagan: oyna yopilib ketadi. Bitim chiqmadi |

## 3. Bitim holatlari (`BitimHolatlariTest`), 25.11, 11:01, ms

Har bir stsenariy o'z bitimida, 8-bitimga tegilmaydi. **Xaridor hisobi ichiga kiriladi** (HKKP → Balans → STIR): bitim bo'yicha har bir operatsiya (kimdan, kimga, summa, maqsad) kutilgani bilan aynan solishtiriladi. Bekor qilishda barcha va erkin mablag' farqi ham tekshiriladi. Sotuvchi bo'yicha faqat tarixdagi jarima va zakalat tekshiriladi, balans raqamlari hali yo'q.

| # | Stsenariy | Bitim | Natija | Tekshirilgani |
|---|---|---|---|---|
| 1 | To'lov → YBHR to'liq | B26000382 | ✅ | Erkin mablag' −900 000; hisobda 6 operatsiya mos; «Bajarildi» |
| 2 | To'lov → YBHR yarmi → kelishuv | B26000383 | ✅ | Erkin +500 000; 8 operatsiya mos; «Qisman bajarildi» |
| 3 | Kelishuv bilan bekor qilish | B26000384 | ⚠️ **tekshirilmadi** | Sahifa 502 Bad Gateway berdi. Oldingi ms run'ida (10:24) o'tgan |
| 4 | Xaridor jarimasi | B26000385 | ✅ | 100 000 TESTIVEST → sotuvchi; barcha −100 000 |
| 5 | Sotuvchi jarimasi | B26000386 | ✅ | 100 000 sotuvchi → TESTIVEST; barcha +100 000, erkin +200 000 |
| 6 | To'lov muddatini uzaytirish | B26000387 | ✅ | 30.11 → 02.12; rad: o'zgarmadi; pul harakati yo'q |
| 7 | Yetkazib berish muddatini uzaytirish | B26000388 | ✅ | 09.12 → 11.12; rad: o'zgarmadi |
| 8 | Tegilmagan bitim | B26000389 | ✅ | «To'lov kutilmoqda», hisobda faqat 4 ta boshlang'ich operatsiya |
| 9 | Shartnoma va to'lov chekini yuklab olish | B26000382 | ❌ | Server 400 (5-bo'lim) |

Yuklash endi faqat bitta to'langan kontraktda, bir marta bajariladi (11:01 dan keyingi o'zgarish). Faylning butun PDF ekani tekshiriladi, ichidagi matn tekshirilmaydi.

YBHR tartibi: shartnoma raqami → faktura raqami, sana, miqdor → **xlsx hujjat yuklanadi** → «Ro'yxatga qo'shish» → «Yuborish» → xaridor YBHR sahifasida tasdiqlaydi.

**11:01 dan keyin qo'shilgan, run'da hali sinalmagan:** 502 bo'lsa sahifani bir marta qayta ochish, birinchi taklifni savdo ochilgach 3,5 s kutib berish, yuklashni bir marta qilish.

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
| 1 | Auksion taklifi (ms) | **999 999 999 lotga taklif qabul qilinadi** (10^15 so'm, hisobda ~9,3 mlrd), savatchada «Talab 1 000 000 107/100» | ❌ Sayt xatosi |
| 2 | Shartnoma va to'lov cheki (ms) | `GET contract/create-pdf/{id}` va `contract/create-payment-pdf/{id}` → `400, state 1259, "Unexpected exception"`. Beqaror: B26000363 shartnomasi yuklanadi, cheki yo'q; B26000374 cheki bir marta yuklanib, keyin rad etildi; B26000382, 389 umuman yuklanmadi | ❌ Sayt xatosi |
| 3 | Moderator (admin1) kirishi (ms) | `400 /api/erp, state 1259`. Parol to'g'ri. 25.11: ~10:26 kirdi, 10:45 va ~10:48 kirmadi, ~10:59 va 11:03 kirdi, 11:25 kirmadi | ❌ Sayt xatosi, beqaror |
| 4 | Shartnomalar sahifasi (ms) | 11:14 atrofida bir marta 502 Bad Gateway | ❌ Sayt xatosi |
| 5 | Qarama-qarshi auksion kontrakti (moderator, app) | Hujjat yuklanmasa ham kontrakt yaratiladi (treyder formasida rad etiladi) | ❌ Sayt xatosi |
| 6 | Birja komissiyasi | Sayt har tomondan **800 so'm = 0,08%** oladi (1 000 000 dan). Foydalanuvchi «0.008» deb yozgan, 0,08% deb tushunilgan | ✅ |
| 7 | Kontrakt formasi | «0,1 % – 10 % oralig'ida bo'lishi kerak» yozuvi to'g'ri qiymatda ham ko'rinadi | ❓ |

Qoidalar (xato emas): mavjuddan ko'p lotga (101) taklif qabul qilinadi va qolgan lot bilan qisman bajariladi; past narxda taklif berish mumkin, lekin savatchada ko'rinmaydi va kontrakt tuzilmaydi.

## 6. Keyingi qadamlar

1. admin1 kirishi tuzalgach oddiy auksionni ms da boshidan oxirigacha yurgizish; 3-stsenariyni tasdiqlash.
2. Savdo tugagandan keyingi taklifni aniq tekshirish; sotuvchi balans raqamlari.
3. Qarama-qarshi auksion: to'liq run va qo'shimcha salbiy holatlar (mijozsiz / bo'sh / kasr / harfli qiymatlar, mablag'dan katta xarid, «Bozor narxida» xarid, ikki marta bosish, sessiya tugagach buyurtma, bekor qilingan buyurtma, kontraktni rad etish, noto'g'ri fayl turi, muddatlar 0, bitta tomon imzolagan bitim, pul bloklanishi va qaytishi).
4. Maxsus savdolar: Spirt, Oltin (Tilla), Kumush, Subsidiya — har biri alohida run.
