# eBirja avtotestlari: ro'yxat va so'nggi natijalar

Oxirgi yangilanish: 26.11.2026, 15:35. Testlar hozir **ms** da ishlaydi: `test-ms-app.ebirja.uz` (treyder), `test-ms-head.ebirja.uz` (admin), E-IMZO kaliti bilan. Eski saytga (`test-app` / `test-head`) o'tish uchun `login.properties` dagi `trader.url` va `admin.url` almashtiriladi, kod o'zgarmaydi.

**Joriy holat:** oxirgi to'liq run 26.11, 15:14 (ms): 16:55, **32 o'tdi, 3 xato, 1 o'tkazildi**. Ikkita xato sayt tomonda (5-bo'lim: 1, 2). Uchinchisi (yetkazish muddati) test kamchiligi edi: E-IMZO bir marta «Parol noto'g'ri» qaytardi, so'rov qayta yuborilmagan. Qayta urinish qo'shildi, shu bitimda alohida yurgizilganda o'tdi (15:32). Lekin o'sha safar E-IMZO xato bermadi, shuning uchun qayta urinishning o'zi hali ishga tushmagan.

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
| 25.11, 11:53 | ms | 3:01 | 13 / 1 / 19 | Test xatosi: yangi qo'shilgan kontrakt tekshiruvi noto'g'ri ustunni qidirgan (tuzatildi) |
| 25.11, 11:57 | ms | 2:31 | 3 / 1 / 6 | Faqat auksion. Test xatosi: savdoga chiqarish jadvalida qator topilmadi (tuzatildi) |
| 25.11, 12:00 | ms | 7:37 | 9 / 1 / 0 | Faqat auksion, kontrakt 140. Xato: 5-bo'lim, 1 |
| **25.11, 12:08** | **ms** | **16:38** | **30 / 2 / 1** | To'liq, kontrakt 141. Xatolar: 5-bo'lim, 1 va 2 |
| 25.11, 12:25 | ms | 7:36 | 9 / 1 / 0 | Faqat auksion, kontrakt 142, faqat o'z bitimlari imzolandi. Xato: 5-bo'lim, 1 |
| 26.11, 09:47 | ms | 17:00 | 32 / 3 / 1 | To'liq, 2 kontrakt (biri rad etildi), bekor qilishda 6 so'rov. Xatolar: 5-bo'lim 1, 2 va 2-bitimda test xatosi (eski balans ishlatilgan, tuzatildi) |
| 26.11, 14:26 | ms | 14:01 | 33 / 2 / 1 | Savdo vaqti 1 daqiqa qilib sinaldi: 2 ta negativ ulgurmadi, savdo vaqti yana 3 daqiqa qilindi |
| **26.11, 15:14** | **ms** | **16:55** | **32 / 3 / 1** | To'liq, kontrakt ID 201 (savdo 167), rad etilgani ID 200. Xatolar: 5-bo'lim 1, 2; yetkazish muddatida E-IMZO paroli (tuzatildi) |

**Muhim tuzatish:** 12:00 dan oldingi run'larda test yangi kontrakt yaratib tasdiqlagan, lekin savdoga jadvaldagi birinchi qatorni, ya'ni **eski 80-kontraktni** chiqargan. Demak, «kontrakt → tasdiqlash → savdo» zanjiri o'sha run'larda to'liq sinalmagan. 12:00 dan boshlab zanjir aniq kontrakt bo'yicha: yuborilgan vaqt (soniyasigacha) + mahsulot + sotuvchi STIR → moderatordagi ID → savdo raqami → savdo va bitimlar faqat shu raqamda (ID 166→140, 167→141, 168→142).

Avvalgi tahrirda «30 testdan 29 tasi» va «30/30» deb yozilgan edi, bu noto'g'ri: o'tkazib yuborilgan test o'tgan deb sanalgan.

26.11, 15:14 dagi run bo'yicha vaqt (logda har qator oldida soat bor):

| # | Qadam | Vaqt | Natija |
|---|---|---|---|
| 1 | Kontrakt salbiy testlari (13 ta) | ~2:10 | ✅ |
| 2 | 2 ta kontrakt yaratish | 0:40 | ✅ |
| 3 | Moderator 1 rolda rad etadi → darhol «Rad etilgan» (sababsiz rad etish ham sinaladi) | 0:23 | ✅ ID 200 |
| 4 | Moderator ikkinchisini 3 rolda tasdiqlaydi | 0:23 | ✅ ID 201 → savdo 167 |
| 5 | Savdo vaqtini yaratish (e'lon 1 daq, savdo 3 daq) | 0:45 | ✅ |
| 6 | E'lonni kutish, minus/0 lot, 100 lotni savdoga qo'yish (5,9 s) | 0:15 | ✅ |
| 7 | Savdo boshlanmasdan taklif | 0:07 | ✅ qabul qilinmadi, «Seksiya vaqti emas» |
| 8 | 12 ta taklif oynasini tayyorlash | 0:14 | ✅ |
| 9 | Sotuvchi o'z mahsulotiga taklif bera olmasligi | 0:24 | ✅ |
| 10 | Savdo ichida: 9 taklif, o'chirish, 101 lot, savatcha 8/1/1, barcha negativlar | 0:56 | ✅ (999 999 999 — sayt xatosi) |
| 11 | Savdo tugashini kutish | 2:06 | |
| 12 | Bitimlarni yig'ish | 0:48 | ✅ 9 ta, 101 lotdan 92 lot |
| 13 | Ommaviy imzolash: trader1, trader2, admin (faqat o'z bitimlari) | 0:59 | ✅ |
| 14 | Bekor qilish: 6 so'rov, 3 rad, 3 qabul, pul va operatsiyalar ikkala tomonda | 2:20 | ✅ |
| 15 | 1-bitim: to'lov, shartnoma va chekni yuklash, to'liq YBHR | 0:44 | ✅ / yuklash ❌ (sayt 400) |
| 16 | 2-bitim: to'lov, yarim YBHR, kelishuv | 1:03 | ✅ |
| 17 | To'lov muddati +2 ish kuni: qabul va rad | 0:36 | ✅ 01.12 → 03.12 |
| 18 | Yetkazish muddati +2 ish kuni: qabul va rad | 1:55 | ❌ E-IMZO paroli; alohida ✅ 14.12 → 16.12 |
| 19 | Tegilmagan bitim | 0:01 | ✅ |

Taklif berishda E-IMZO so'ralmaydi: 9 ta taklif 17 s. Savdodagi ish ~1 daqiqada tugaydi, qolgan ~2 daqiqa savdo yopilishi kutiladi (bitimlar faqat yopilgandan keyin tuziladi).

## 3a. Bekor qilish (26.11, 15:14)

Ikki treyder uchun bittadan brauzer ochiq turadi, test ular orasida almashadi. 6 ta so'rov yuboriladi: 2 kelishuv, 2 sotuvchi jarimasi, 2 xaridor jarimasi. Xaridor 3 tasini rad etadi (har turdan bittasi), 3 tasini qabul qiladi.

| Holat | Xaridor (barcha / erkin) | Sotuvchi (barcha / erkin) | Natija |
|---|---|---|---|
| 3 ta rad etilgan so'rov | 0 / 0 | 0 / 0 | ✅ ortiqcha pul yechilmadi, holat «To'lov kutilmoqda» |
| Kelishuv | 0 / +100 000 | 0 / +100 000 | ✅ |
| Xaridor jarimasi | −100 000 / 0 | +100 000 / +200 000 | ✅ |
| Sotuvchi jarimasi | +100 000 / +200 000 | −100 000 / 0 | ✅ |

Har 6 bitimning operatsiyalari ikkala hisobda (xaridor hisobi ichida va sotuvchi broker balansida) aynan solishtiriladi: 12 tadan 12 tasi mos. Salbiy: bekor qilish formasi bo'sh / faqat tur / faqat sabab / sabab faqat probel — rad etiladi; xaridor so'rovni sababsiz rad eta olmaydi; so'rov kutilayotganda ikkinchi so'rov yuborib bo'lmaydi.

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

## 2. Oddiy auksion (`AuksionOddiyTest`), 25.11, 12:08, ms

| # | Qadam | Holat |
|---|---|---|
| 1 | Treyder 1 kontrakt yaratadi | ✅ |
| 2 | Moderator 3 rolda tasdiqlaydi | ✅ (kirish goh ishlamaydi, 5-bo'lim) |
| 3 | Admin savdo vaqtini o'zi yaratadi (e'lon 1 daq, savdo 3 daq) | ✅ |
| 4 | 100 lot savdoga; salbiy: **-5 lot** (minus kiritilmaydi), **0 lot** («Grafik lot yuborilishi kerak») | ✅ |
| 5 | Treyder 2 TESTIVEST nomidan 1 lotdan 9 ta taklif, bittasi 🗑 bilan o'chiriladi | ✅ o'chirilganidan bitim chiqmadi |
| 6 | 8 ta kontrakt (B26000400–407) | ✅ |
| 7 | 101 lotga taklif (100 lot, 8 tasi sotilgan) | ✅ qolgan **92 lotni** oldi (B26000408) |
| 8 | Ikkala treyder va admin **faqat shu savdodan chiqqan bitimlarni** belgilab imzolaydi/tasdiqlaydi | ✅ 12:25 dagi run'da sinaldi: uchala tomonda 9 tadan 9 tasi (B26000412–420), begona qator yo'q; «Tasdiqlangan» tabida 9 ta |

Begona bitim belgilanib qolsa, test imzolamasdan yiqiladi. Jadvalda o'sha paytda begona bitim bor-yo'qligi logga yozilmagan, shuning uchun bu himoya begona bitim bilan sinalgan deb bo'lmaydi.

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

## 3. Bitim holatlari (`BitimHolatlariTest`), 25.11, 12:08, ms

Har bir stsenariy o'z bitimida, 8-bitimga tegilmaydi. **Xaridor hisobi ichiga kiriladi** (HKKP → Balans → STIR): bitim bo'yicha har bir operatsiya (kimdan, kimga, summa, maqsad) kutilgani bilan aynan solishtiriladi. Bekor qilishda barcha va erkin mablag' farqi ham tekshiriladi. 26.11 dan boshlab sotuvchi ham to'liq tekshiriladi: broker balansi (barcha / erkin) va har bir operatsiya.

| # | Stsenariy | Bitim | Natija | Tekshirilgani |
|---|---|---|---|---|
| 1 | To'lov → YBHR to'liq | B26000400 | ✅ | Erkin mablag' −900 000; hisobda 6 operatsiya mos; «Bajarildi» |
| 2 | To'lov → YBHR yarmi → kelishuv | B26000401 | ✅ | Erkin +500 000; 8 operatsiya mos; «Qisman bajarildi» |
| 3 | Kelishuv bilan bekor qilish | B26000402 | ✅ | Erkin +100 000, barcha o'zgarmadi; 5 operatsiya mos; jarima yo'q |
| 4 | Xaridor jarimasi | B26000403 | ✅ | 100 000 TESTIVEST → sotuvchi; barcha −100 000 |
| 5 | Sotuvchi jarimasi | B26000404 | ✅ | 100 000 sotuvchi → TESTIVEST; barcha +100 000, erkin +200 000 |
| 6 | To'lov muddatini uzaytirish | B26000405 | ✅ | 30.11 → 02.12; rad: o'zgarmadi; pul harakati yo'q |
| 7 | Yetkazib berish muddatini uzaytirish | B26000406 | ✅ | 09.12 → 11.12; rad: o'zgarmadi |
| 8 | Tegilmagan bitim | B26000407 | ✅ | «To'lov kutilmoqda», hisobda faqat 4 ta boshlang'ich operatsiya |
| 9 | Shartnoma va to'lov chekini yuklab olish | B26000400 | ❌ | Server 400 (5-bo'lim) |
| 9a | Xaridor: alohida va «Barchasini yuklab olish» (26.11 17:25, 17:31) | B26000488 | ✅ | 15:27 da shu bitimda 400 edi, keyin ikkala usulda ham 2 ta haqiqiy PDF tushdi |

Yuklash faqat bitta to'langan kontraktda, bir marta bajariladi. Endi «Barchasini yuklab olish» ham tekshiriladi (ZIP bo'lsa ichidagi fayllar soni). Server xato qaytarsa, xabar yo'qolgach bir marta qayta urinadi; birinchi xato logda qoladi. Faylning butun PDF ekani tekshiriladi, ichidagi matn tekshirilmaydi.

YBHR tartibi: shartnoma raqami → faktura raqami, sana, miqdor → **xlsx hujjat yuklanadi** → «Ro'yxatga qo'shish» → «Yuborish» → xaridor YBHR sahifasida tasdiqlaydi.

11:01 dan keyin qo'shilganlardan 12:08 da sinaldi: birinchi taklifni savdo ochilgach 3,5 s kutib berish, yuklashni bir marta qilish. 502 bo'lsa sahifani qayta ochish sinalmagan, chunki 502 qayta chiqmadi.

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
| 2 | Shartnoma va to'lov cheki (ms) | `GET contract/create-pdf/{id}` va `contract/create-payment-pdf/{id}` → `400, state 1259, "Unexpected exception"`. Beqaror: B26000363 shartnomasi yuklanadi, cheki yo'q; B26000374 cheki bir marta yuklanib, keyin rad etildi; B26000382, 389 va 400 umuman yuklanmadi. 26.11: B26000488 15:27 da 400, 17:25 va 17:31 da yuklandi | ❌ Sayt xatosi |
| 3 | Moderator (admin1) kirishi (ms) | `400 /api/erp, state 1259`. Parol to'g'ri. 25.11: ~10:26 kirdi, 10:45 va ~10:48 kirmadi, ~10:59 va 11:03 kirdi, 11:25 kirmadi, ~11:58 dan keyingi 4 ta run'da kirdi | ❌ Sayt xatosi, beqaror |
| 4 | Shartnomalar sahifasi (ms) | 11:14 atrofida bir marta 502 Bad Gateway | ❌ Sayt xatosi |
| 5 | Qarama-qarshi auksion kontrakti (moderator, app) | Hujjat yuklanmasa ham kontrakt yaratiladi (treyder formasida rad etiladi) | ❌ Sayt xatosi |
| 6 | Birja komissiyasi | Sayt har tomondan **800 so'm = 0,08%** oladi (1 000 000 dan). Foydalanuvchi «0.008» deb yozgan, 0,08% deb tushunilgan | ✅ |
| 7 | Kontrakt formasi | «0,1 % – 10 % oralig'ida bo'lishi kerak» yozuvi to'g'ri qiymatda ham ko'rinadi | ❓ |

Qoidalar (xato emas): mavjuddan ko'p lotga (101) taklif qabul qilinadi va qolgan lot bilan qisman bajariladi; past narxda taklif berish mumkin, lekin savatchada ko'rinmaydi va kontrakt tuzilmaydi.

## 6. Audit (TODO.md) natijasi

| Band | Holat |
|---|---|
| A1, A2 | ✅ `editRow` yo'q (savdo qatori o'chirilib, yangidan yaratiladi); kontrakt treyderning «Listingga yuborilgan» ro'yxatida tekshiriladi |
| A3, A4, A5, A6 | ✅ Yaratilgan savdo qatori, «Muvaffaqiyatli» xabari, takliflar soni va «Tasdiqlangan» tabi ijobiy tekshiriladi |
| A7 | ✅ Boshqacha usulda: belgi ro'yxatlarda ko'rinmaydi, shuning uchun kontrakt vaqti + mahsulot + STIR bo'yicha topiladi |
| A8, A9 | ✅ Parol faqat E-IMZO oynasi oldinda bo'lsa, clipboard orqali yoziladi |
| A10 | ⚠️ Qisman: kutishlardagi kutilmagan xatolar logga yoziladi, lekin kodda `catch (Exception ignore)` lar hali bor |
| A11, A12, A13, A14 | ✅ Toshkent vaqti; `probe/` va `LoginPage` o'chirildi; `pom.xml` tozalandi |
| B1, B2, B3 | ✅ `Config` tartibi, `-Denv`, classpath |
| B4, B5 | ❌ Qilinmadi (Maven wrapper, `smoke`/`e2e` guruhlari) |
| B6, B7, B8 | Boshqa jamoalarga so'rov, kodga bog'liq emas |
| C, D, E | Playwright (TypeScript) ga ko'chish, boshlanmagan |

## 7. Keyingi qadamlar

1. To'liq jarayonni oxirgi o'zgarish bilan yana bir marta yurgizish. Playwright + TypeScript PoC (Node.js o'rnatilgach).
2. Savdo tugagandan keyingi taklifni aniq tekshirish; sotuvchi balans raqamlari.
3. Qarama-qarshi auksion: to'liq run va qo'shimcha salbiy holatlar (mijozsiz / bo'sh / kasr / harfli qiymatlar, mablag'dan katta xarid, «Bozor narxida» xarid, ikki marta bosish, sessiya tugagach buyurtma, bekor qilingan buyurtma, kontraktni rad etish, noto'g'ri fayl turi, muddatlar 0, bitta tomon imzolagan bitim, pul bloklanishi va qaytishi).
4. Maxsus savdolar: Spirt, Oltin (Tilla), Kumush, Subsidiya — har biri alohida run.
