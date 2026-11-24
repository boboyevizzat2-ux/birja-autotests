package uz.ebirja.tests;

import org.testng.annotations.Test;
import uz.ebirja.config.Config;

import static uz.ebirja.base.Ui.loginByKey;

/**
 * YORDAMCHI (test emas): admin berilgan bitimlarni 'Tasdiqlanmagan' tabidan tasdiqlaydi.
 * Ishlatish: mvn test -Dsurefire.suiteXmlFiles= -Dtest=AdminBitimTasdiqlash#tasdiqla -Dbitimlar=B26000242,B26000243
 */
public class AdminBitimTasdiqlash extends AuctionFlowTest {
    @Override
    protected String tur() { return "oddiy"; }

    @Test
    public void tasdiqla() {
        String list = System.getProperty("bitimlar", "");
        if (list.isBlank()) throw new RuntimeException("-Dbitimlar=B2600...,B2600... berilmagan");
        openBrowser();
        String base = Config.adminUrl();
        loginByKey(driver, base, Config.get("admin2.login"), false);
        for (String no : list.split(",")) adminTasdiqla(base, no.trim());
    }

    /** Xaridor (trader2) imzolaydi, keyin admin tasdiqlaydi. -Dbitimlar=B26000255 */
    @Test
    public void xaridorImzolabAdminTasdiqlasin() {
        String list = System.getProperty("bitimlar", "");
        if (list.isBlank()) throw new RuntimeException("-Dbitimlar=B2600...,B2600... berilmagan");
        for (String no : list.split(",")) dealNos.add(no.trim());
        dealNo = dealNos.get(0);
        openBrowser();
        signDeal(Config.traderUrl(), "trader2", "Sotib olish");
        String base = Config.adminUrl();
        loginByKey(driver, base, Config.get("admin2.login"), false);
        adminOmmaviyTasdiqla(base, dealNos);
    }

    /** Faqat savdo vaqtini qo'yish qadamini sinash (eski qatorlar o'chiriladi, yangilari yaratiladi). */
    @Test
    public void savdoVaqtiniSinash() { step3_savdoVaqtiniQoyish(); }
}
