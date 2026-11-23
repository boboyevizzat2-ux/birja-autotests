# eBirja avtotestlari: ro'yxat va so'nggi natijalar

Oxirgi yangilanish: 23.11.2026. Barcha testlar test saytida (`test-app.ebirja.uz`, `test-head.ebirja.uz`) E-IMZO kaliti bilan ishlaydi.

## Qanday ishga tushiriladi

IntelliJ'da yuqoridagi ro'yxatdan tugmani tanlab ▶ bosiladi. Test vaqtida klaviatura va sichqonchaga tegmang, chunki E-IMZO paroli avtomatik kiritiladi.

| Tugma | Test klassi | Nima qiladi | Taxminiy vaqt |
|---|---|---|---|
| Auksion - Oddiy | `AuksionOddiyTest` | To'liq auksion, boshidan oxirigacha | 15 daqiqa |
| Auksion - Spirt / Oltin / Kumush / Subsidiya | `Auksion<Tur>Test` | Shu savdo turidagi auksion (hozircha faqat mahsuloti farq qiladi) | 15 daqiqa |
| Kontrakt - negativ | `KontraktNegativTest` | Kontrakt formasidagi salbiy holatlar | 8 daqiqa |
| Bitim holatlari | `BitimHolatlariTest` | To'lov, bekor qilish (3 turi), muddatni uzaytirish | har biri 1,5–3 daqiqa |

Bitta holatni alohida ishga tushirish: `mvn test -Dsurefire.suiteXmlFiles= -Dtest=BitimHolatlariTest#tolovQilish`

Sozlamalar `login.properties` faylida:

| Sozlama | Ma'nosi | Hozirgi qiymati |
|---|---|---|
| `auction.lots` | Savdoga chiqariladigan lotlar | 100 |
| `auction.bids` | Nechta alohida taklif beriladi | 7 |
| `auction.bidPauseMs` | Takliflar orasidagi pauza | 2000 (2 soniya) |
| `auction.minutes` | E'lon va savdo davomiyligi | 2 daqiqa |
| `auction.buyerClient` | Xaridor qaysi mijoz nomidan oladi | 313141918 (TESTIVEST MCHJ) |

## 1. Auksion: to'liq jarayon (`AuksionOddiyTest`)

Natija: **9/9 o'tdi** (23.11.2026, 21:10). Bitimlar: B26000256–B26000262.

| # | Qadam | Holat |
|---|---|---|
| 1 | Treyder 1 kontrakt yaratadi (E-IMZO) | ✅ |
| 2 | Moderator 3 rolda tasdiqlaydi | ✅ |
| 3 | Admin savdo vaqtini qo'yadi (e'lon 2 daq, savdo 2 daq) | ✅ |
| 4 | Treyder 1 100 lotni savdoga chiqaradi | ✅ |
| 5 | Treyder 2 (TESTIVEST nomidan) 7 ta oynani oldindan tayyorlab, 2 soniya oraliq bilan 1 lotdan 7 ta taklif beradi | ✅ (har bir taklif ≈ 7,5 s) |
| 6 | 7 ta bitim hosil bo'ladi | ✅ |
| 7 | Ikkala treyder ommaviy imzolaydi (treyder 2: «Mijozimning shartnomalari») | ✅ |
| 8 | Admin tasdiqlaydi («Ommaviy tasdiqlash», tugma bo'lmasa bittalab) | ✅ |
| 9 | Salbiy holatlar yakuni | ✅ |

### Auksiondagi salbiy holatlar

| Holat | Kutilgan | Natija |
|---|---|---|
| Savdoga **-5 lot** chiqarish | Minus kiritilmaydi | ✅ maydonda «5» qoladi |
| Savdoga **0 lot** chiqarish | Rad etiladi | ✅ «Grafik lot yuborilishi kerak», imzo so'ralmaydi |
| 100 lotli savdoga **101 lotga taklif** | Rad etilishi kerak | ⚠️ **O'chirilgan** (pastga qarang) |

⚠️ **101 lotli taklif haqida:** 23.11 dagi run'da bu taklif haqiqatan yuborilib ketdi. Sayt uni rad etmadi va savdodagi **hamma 100 lotni bitta bitimga** berdi (B26000255, TESTIVEST, 100 000 000 so'm). Sayt mavjuddan ko'p lotga berilgan taklifni rad etmay, bor lot bilan bajaradi. Bu kutilgan qoidami yoki xatomi, aniqlash kerak. Test xavfsiz usul topilmaguncha o'chirib qo'yilgan, yoqish uchun: `auction.negativ101=true`.

## 2. Kontrakt yaratish: salbiy holatlar (`KontraktNegativTest`)

Natija: **13 tadan 12 tasi o'tdi, 1 ta xato topildi** (23.11.2026).

Xavfsizlik: salbiy holatlarda brauzerdan E-IMZO'ga ketadigan imzo so'rovi to'sib qo'yiladi. Forma tekshiruvdan o'tib ketsa ham kontrakt yuborilmaydi.

| Holat | Natija |
|---|---|
| Narxga minus (-500) | ✅ minus kiritilmaydi |
| Lot hajmiga minus | ✅ minus kiritilmaydi |
| Sotuvchi zakalatiga minus | ✅ minus kiritilmaydi |
| Xaridor zakalatiga minus | ✅ minus kiritilmaydi |
| Yetkazib berish muddatiga minus | ✅ minus kiritilmaydi |
| To'lov muddatiga minus | ✅ minus kiritilmaydi |
| **Narx = 0** | ❌ **XATO: forma o'tadi va imzo so'raydi** |
| Sotuvchi zakalati 0% | ✅ rad etiladi: «0,1 % – 10 % oralig'ida bo'lishi kerak» |
| Sotuvchi zakalati 15% | ✅ maydon 10% dan ortig'ini kiritishga yo'l qo'ymaydi («1» qoladi) |
| Xaridor zakalati 0% | ✅ rad etiladi |
| Xaridor zakalati 15% | ✅ maydon yo'l qo'ymaydi |
| Bo'sh forma | ✅ rad etiladi: «Majburiy maydon», «kamida 1 ta fayl kiritilishi kerak» |
| Hujjat yuklanmagan | ✅ rad etiladi: «kamida 1 ta fayl kiritilishi kerak» |

## 3. Bitim holatlari (`BitimHolatlariTest`)

Bitimlar «To'lov kutilmoqda» holatida bo'lishi kerak. Balans HKKP → Balans sahifasidan tekshiriladi: mijozlar uchun «Mijozlar balansi», operatsiyalar tarixi uchun «Broker balansi».

| Holat | Bitim | Natija | Tekshirilgani |
|---|---|---|---|
| **To'lov** (xaridor) | B26000256 | ✅ | «Muvofaqiyatli to'landi», holat «To'langan». TESTIVEST erkin mablag'i **900 000** ga kamaydi (1 000 000 − 100 000 zakalat) |
| **Bekor qilish: Kelishuv** | B26000242 | ✅ | Sotuvchi so'rov yuboradi, xaridor «Bekor qilish so'rovi» sahifasida tasdiqlaydi, holat «Bekor qilingan», zakalat blokdan chiqadi |
| **Bekor qilish: Xaridor jarimasi** | B26000243 | ✅ | **100 000 so'm** (zakalat) xaridordan sotuvchiga o'tadi |
| **Bekor qilish: Sotuvchi jarimasi** | B26000244 | ✅ | **100 000 so'm** (zakalat) sotuvchidan xaridorga o'tadi |
| **To'lov muddatini uzaytirish** | B26000247 | ✅ | Sotuvchi 2 ish kuniga so'raydi, xaridor qabul qiladi |
| Yetkazib berish muddatini uzaytirish | B26000257 | ⏳ yozilgan, hali ishga tushirilmagan | |
| YBHR yuborish va tasdiqlash, «Bajarildi» | — | ⏳ hali yozilmagan | |
| YBHR'ning yarmi, keyin kelishuv, «Qisman bajarildi» | — | ⏳ hali yozilmagan | |

Eslatma: avvalgi bitimlar IBOD-AZIZ KELAJAGI'ning o'z nomidan edi. Uning hisobida pul yo'q («Hisobda mablag' yetarli emas»), shuning uchun to'lov TESTIVEST MCHJ mijozi nomidan sinaladi.

## 4. Topilgan xatolar va savollar

| # | Joy | Nima | Holat |
|---|---|---|---|
| 1 | Kontrakt yaratish | **Narx 0 bo'lsa ham forma o'tadi va imzo so'raydi** | ❌ Xato |
| 2 | Auksion, taklif | 100 lotli savdoga 101 lotga taklif rad etilmaydi, 100 lotga bajariladi | ❓ Qoidami yoki xatomi, aniqlash kerak |
| 3 | Auksion, taklif | 10 ta tez taklifdan 7 ta bitim chiqdi (20:00 dagi savdo). 7 ta taklifda 7 tasi chiqadi | ❓ Sababini aniqlash kerak |
| 4 | Kontrakt formasi | «0,1 % – 10 % oralig'ida bo'lishi kerak» yozuvi to'g'ri qiymatda ham ko'rinib turadi | ❓ Avvalroq kuzatilgan |

## 5. Hali qilinmaganlar

- Sotuvchi o'z mahsulotiga taklif berib ko'rishi (salbiy holat).
- YBHR: to'liq va yarim, «Bajarildi» va «Qisman bajarildi».
- So'rovlarni rad etish (bekor qilish va muddat uzaytirish so'rovlarida «Bekor qilish» tugmasi).
- Moderator kontraktni ochib, fayllarini yuklab olishini tekshirish (tugma topilgan: «Hujjatlarni yuklab olish»).
- Spirt, Oltin, Kumush, Subsidiya: o'ziga xos qadamlar (xaridorlar ro'yxati, limit, spread).
