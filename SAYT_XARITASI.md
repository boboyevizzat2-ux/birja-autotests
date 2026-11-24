# eBirja test sayti xaritasi

Avtotestlar uchun o'rganib chiqilgan: 21.11.2026.
❓ belgisi bor joylar taxmin qilib yozilgan, ularni tasdiqlash kerak.

## 1. Saytlar va akkauntlar

| Kabinet | Manzil | Akkaunt (`login.properties`) | Rol |
|---|---|---|---|
| Treyder | https://test-ms-app.ebirja.uz | `trader1`: NEXTGEN CATERING MCHJ | Treyder |
| Treyder | https://test-ms-app.ebirja.uz | `trader2`: IBOD-AZIZ KELAJAGI XK | Treyder |
| Moderator | https://test-ms-head.ebirja.uz | `admin1`: Moxira T. T. | 17 ta rol, almashtiriladi (asosiysi `MODERATOR_LISTING_MAIN`) |
| Admin | https://test-ms-head.ebirja.uz | `admin2` | `ADMIN`: 103 ta sahifa, hammasini ko'radi |

**Kirish xususiyatlari**
- Treyder saytida "Birja savdolari" platformasini **login-parol sahifasining o'zida** tanlash kerak.
- Ikkala saytda ham ERI kalit (E-IMZO) yoki login-parol bilan kirish mumkin.
- Kontrakt yuborish, tasdiqlash va bitimni imzolash **E-IMZO imzosini** talab qiladi. Test kaliti paroli `login.properties` faylida saqlangan.
- Test saytlari faqat **VPN** orqali ochiladi, VPN bo'lmasa "403" chiqadi.

## 2. Asosiy jarayon: auksion savdosi (boshidan oxirigacha)

| # | Kim | Qayerda | Nima qiladi |
|---|---|---|---|
| 1 | Treyder 1 | Listing → Auksion → **Kontrakt yaratish** (`/trader/listing/sales/create`) | Broker yoki mijoz nomidan kontrakt yaratadi va **E-IMZO** bilan yuboradi |
| 2 | Moderator, `MODERATOR_LISTING_MAIN` | Listing → Kontraktlar → "Yangi" | 1-bo'lib tasdiqlaydi |
| 3 | Moderator, `MODERATOR_LISTING` | Listing → Kontraktlar | 2-bo'lib tasdiqlaydi |
| 4 | Moderator, `MODERATOR_LISTING_MEMBER` | Listing → Kontraktlar | Tasdiqlaydi. Jadvalda "Komissiya raisi / kotibi / a'zosi" ustunlari bor ❓ |
| 5 | Treyder 1 | Listing → Auksion | Kontrakt "Tasdiqlangan" holatiga o'tadi |
| 6 | Admin | Auksion Savdolar 2 → **Savdo uchun e'lon berish** | Ertalabki savdoni e'lon qiladi va 2 ta savdo turining vaqtini belgilaydi (test uchun oralig'i 1 daqiqa) |
| 7 | Treyder 1 | Savdolar → **Auksion savdolar** | Mahsulotni lotlarga bo'lib savdoga chiqaradi (masalan, 100 lot) |
| 8 | Treyder 2 | Savdolar → Auksion savdolar | Savdo vaqtida taklif beradi |
| 9 | Tizim | — | Bitim (shartnoma) hosil bo'ladi |
| 10 | Treyder 1, Treyder 2 | Shartnomalar → **Mening shartnomalarim** | Bitimni imzolaydi (E-IMZO) |
| 11 | Admin | **Bitimlar** → tasdiqlanmagan bitimlar | Bitimni imzolaydi (E-IMZO) |

## 3. Treyder kabineti (21 ta sahifa)

| Bo'lim | Sahifa | Asosiy amallar |
|---|---|---|
| Profil | `/trader/profile` | Ma'lumotlar, bank rekvizitlari, sertifikatni yuklab olish, Mijozlar va Broker reestri |
| Listing | Auksion `/trader/listing/sales` | **Kontrakt yaratish**, qidirish, sana filtri, eksport |
| | Teskari auksion `/trader/listing/rfq` | Kontrakt yaratish (sotib oluvchi sifatida) |
| Savdoga chiqarish rejasi | `/trader/plan-graph` | Oylik reja, eksport |
| Savdolar | Auksion savdolar `/trader/sales/auction-sales` | **Savdoga chiqarish**, mijoz tanlash, shablon orqali savdoga qo'yish |
| | Qarama-qarshi auksion savdolari | Qidirish |
| | Teskari auksion savdolar | Qidirish |
| Shartnomalar | Mening shartnomalarim | **Ommaviy imzolash**, muddatlarni uzaytirish, ZIP, eksport |
| | Mijozimning shartnomalari | Xuddi shunday |
| | To'lov muddatini uzaytirish so'rovi | Ommaviy tasdiqlash yoki bekor qilish |
| | Yetkazib berish muddatini uzaytirish so'rovi | Ommaviy tasdiqlash yoki bekor qilish |
| | Bekor qilish so'rovi | Ko'rish, eksport |
| | Logistika bo'yicha bitimlar | Ko'rish |
| HKKP | Balans | Barcha, erkin va band mablag'lar, qarzdorlik |
| | YBHR | **YBHR yuborish** |
| Arbitraj | `/common/arbitration` | **Ariza yaratish**, eksport |
| Boshqalar | Yo'riqnoma, Xabarnomalar, Xavfsizlik (faol seanslar), Sozlamalar, Bozor tahlili | Ko'rish |

⚠️ **Sozlamalar** (`/common/settings`) sahifasida sarlavhada "Xavfsizlik" yozuvi chiqadi va sahifa bo'sh. Bu xato bo'lishi mumkin ❓

## 4. Moderator kabineti: rollar

Rol yuqoridagi ism ustiga bosib, **"Rol almashtrish" → rolni tanlash → "Saqlash"** orqali almashtiriladi.

| Rol | Sahifalar soni | Asosiy vazifasi (sahifalarga qarab) |
|---|---|---|
| `MODERATOR_LISTING` | 30 | Kontraktlarni tasdiqlash, mahsulotlar ma'lumotnomasi (guruh, bo'lim, rang, koeffitsient), limitlar, etil spirti arizalari |
| `MODERATOR_LISTING_MAIN` | 17 | Kontraktlarni tasdiqlash (asosiy), tartibga solinadigan mahsulotlar, hisobotlar |
| `MODERATOR_LISTING_MEMBER` | 9 | Kontraktlarni tasdiqlash (a'zo), mahsulot nomlari va koeffitsientlari |
| `MODERATOR_MAKLERIYAT_MAIN` | 59 | Bitimlar, savdo kalendari, hisobotlar, HKKP, monopoliyaga qarshi kurash |
| `MODERATOR_MAKLERIYAT` | 18 | Bitimlar, sotish grafigi, nazorat va arbitraj arizalari |
| `MODERATOR_CLEARING` | 89 | HKKP (balans, YBHR, birikmagan pullar, tariflar, fakturalar), BBIT jamg'armasi, hamkorlik dasturi, rollar, loglar |
| `MODERATOR_REGISTRATION` | 8 | Mijozlar, brokerlar, treyderlar ro'yxati |
| `MODERATOR_REGISTRATION_MAIN` | 13 | Ro'yxatdan o'tkazish (asosiy), reestrlar |
| `MODERATOR_PLAN_GRAPHIC` | 3 | Sotish grafigi |
| `MODERATOR_ARBITRATION` | 10 | Arbitraj komissiyasi, arizalar, norezidentlar |
| `MODERATOR_ARBITRATION_SECRETARY` | 5 | Arbitraj kotibi |
| `MODERATOR_ARBITRATION_MEMBER` | 5 | Arbitraj a'zosi, bitimlar |
| `MODERATOR_COMPLAINT_MEMBER` | 2 | Shikoyatlar ❓ (faqat profil va hujjatlar ko'rinadi) |
| `MODERATOR_COMPLAINT_SECRETARY` | 2 | Shikoyatlar kotibi ❓ |
| `MODERATOR_CONTROL` | 11 | Nazorat komissiyasi (shartnomalar bo'yicha), BHM |
| `MODERATOR_CONTROL_SECRETARY` | 1 | Faqat profil ❓ |
| `MODERATOR_CONTROL_MEMBER` | 3 | Nazorat komissiyasi a'zosi |

## 5. Admin kabineti (`ADMIN`, 103 ta sahifa)

| Bo'lim | Ichidagi sahifalar |
|---|---|
| Bitimlar | Bitimlar, Bitimlar (to'lov kuni bugun), Narx dinamikasi, YBHR limit mijozlar |
| Me'yoriy hujjatlar | Oferta, Me'yoriy-huquqiy hujjatlar, Treyder testlari |
| Sotish grafigi | Sotish grafigi, Tahrirlash so'rovlari |
| Rahbariyat monitoringi | Birja savdolari hisoboti, Tashkilot rejasi, Xodimlar rejasi |
| Hisobotlar | Hisobvaraq, Bitimlar / Auksion / Teskari auksion reestrlari, monitoring, realizatsiya hisobotlari |
| Birja ishtirokchilari | Mijozlar, Brokerlar, Treyderlar, 2038 hisobotlari, Norezidentlar |
| Listing | Kontraktlar, Qarama-qarshi va Teskari auksion kontraktlari, Mahsulotlar, Maxsus savdolar, Tartibga solinadigan mahsulotlar |
| **Auksion Savdolar 2** | **Boshqaruv**, **Savdo uchun e'lon berish**, Savdoga qo'yish vaqtini uzaytirish |
| Teskari auksion savdo | Sessiya, Sotib olish uchun e'lon berish |
| Qarama-qarshi auksion | Savdo sessiyasi, Savdo kalendari |
| BBIT jamg'armasi | Kafolat limiti, Kafolat ijrosi, Jamg'arma shartnomalari |
| Hodimlar bonusi | Biriktirish, Hisoblangan bonuslar, Reestr, Xodimlar |
| Birja komissiyalari | Arbitraj va Nazorat komissiyalari, arizalar, ijro |
| Ma'lumotnomalar | Birliklar, Pul birliklari, Rad sabablari, BHM |
| Komissiyalar, Hamkorlik dasturi, Monopoliyaga qarshi kurash | Ro'yxatlar va hisobotlar |
| Boshqalar | Rollar, Loglar, Moderatorlar, Yangiliklar, Videomateriallar, Landing kontenti, Telefon versiyasi |

Adminda ham rol almashtirish bor, lekin ro'yxatida qaysi rollar borligi hali aniqlanmagan ❓

## 6. Kuzatilgan muammolar (tekshirib ko'rish kerak)

1. Treyder → **Sozlamalar** sahifasi bo'sh, sarlavhasi "Xavfsizlik".
2. Admin → **Hisobotlar** (Bitimlar reestri va boshqalar): jadval ustunlarida o'zbekcha va kirillcha nomlar aralashib ketgan.
3. Kontrakt formasida "0,1% – 10% oralig'ida bo'lishi kerak" degan yozuv to'g'ri qiymat (10) kiritilganda ham ko'rinib turadi.

## 7. Avtotest rejasi

| Bosqich | Nima tekshiriladi | Holat |
|---|---|---|
| 1 | Kirish: 2 ta treyder, moderator, admin, noto'g'ri login, bo'sh maydonlar | Treyderlarniki tayyor ✅ |
| 2 | Har bir kabinetdagi barcha sahifalar ochilishi (har bir moderator roli bilan) | Navbatda |
| 3 | Moderatorda rol almashtirish | Navbatda |
| 4 | Filtrlar, qidiruv, tablar | Navbatda |
| 5 | **To'liq auksion jarayoni** (2-bo'lim, E-IMZO bilan) | Navbatda |
