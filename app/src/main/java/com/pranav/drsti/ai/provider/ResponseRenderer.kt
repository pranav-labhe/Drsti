package com.pranav.drsti.ai.provider

import com.pranav.drsti.model.*

/**
 * Renders structured Jyotish findings into human-friendly natural language.
 * Completely filters technical jargon (house numbers, planet names in prose).
 */
object ResponseRenderer {

    fun render(
        findings: List<JyotishFinding>,
        detailLevel: DetailLevel,
        language: String
    ): String {
        if (findings.isEmpty()) return getEmptyStateText(language)

        val sb = StringBuilder()
        
        // Orchestrated Delivery: Intro -> Key Finding -> Practical Implication -> Encouragement
        sb.append(getIntro(language)).append(" ")

        // 1. Theme (Overarching vibe)
        findings.firstOrNull { it.category == FindingCategory.THEME }?.let {
            sb.append(getThemeText(it.key, language)).append(" ")
        }

        // 2. Placement (Area of life focus)
        findings.firstOrNull { it.category == FindingCategory.PLACEMENT }?.let {
            sb.append(getPlacementText(it.key, language)).append(" ")
        }

        // 3. Axis (MD/AD relationship)
        findings.firstOrNull { it.category == FindingCategory.AXIS }?.let {
            sb.append(getAxisText(it.key, language)).append(" ")
        }

        // 4. Notable Transits
        findings.filter { it.category == FindingCategory.TRANSIT }.forEach {
            sb.append(getTransitText(it.key, language)).append(" ")
        }

        // 5. Yogas
        findings.firstOrNull { it.category == FindingCategory.YOGA }?.let {
            sb.append(getYogaText(it.key, language)).append(" ")
        }

        if (detailLevel == DetailLevel.ELABORATE) {
            sb.append("\n\n").append(getElaborateClosing(language))
        }

        return sb.toString().trim()
    }

    private fun getIntro(lang: String) = when (lang) {
        "hi" -> "मैंने आपके चार्ट का सावधानीपूर्वक विश्लेषण किया है।"
        "mr" -> "मी तुमच्या कुंडलीचे काळजीपूर्वक विश्लेषण केले आहे."
        else -> "I have carefully analyzed your chart."
    }

    private fun getEmptyStateText(lang: String) = when (lang) {
        "hi" -> "मैं आपके चार्ट देख रहा हूँ, लेकिन विशिष्ट व्याख्या प्रदान करने के लिए मुझे अधिक विवरण चाहिए।"
        "mr" -> "मी तुमचा तक्ता पाहत आहे, परंतु विशिष्ट स्पष्टीकरण देण्यासाठी मला अधिक तपशीलांची आवश्यकता आहे."
        else -> "I'm looking at your chart, but I need more details to provide a specific interpretation."
    }

    private fun getThemeText(key: String, lang: String): String = when (lang) {
        "hi" -> when (key) {
            "THEME_SUN" -> "यह आत्म-खोज और अपनी पहचान स्थापित करने का समय है।"
            "THEME_MOON" -> "भावनात्मक कल्याण और मानसिक शांति अभी प्राथमिक फोकस हैं।"
            "THEME_MARS" -> "गतिविधि और महत्वाकांक्षा उच्च हैं। आप काफी ऊर्जावान महसूस करेंगे।"
            "THEME_MERCURY" -> "संचार और बुद्धिमत्ता अभी आपके सबसे बड़े सहायक हैं।"
            "THEME_JUPITER" -> "विस्तार और ज्ञान का समर्थन मिल रहा है।"
            "THEME_VENUS" -> "रिश्ते और रचनात्मकता अभी केंद्र में हैं।"
            "THEME_SATURN" -> "अनुशासन और दीर्घकालिक जिम्मेदारी की मांग की जा रही है।"
            "THEME_RAHU" -> "अचानक परिवर्तन और तीव्र इच्छाएं उभर सकती हैं।"
            "THEME_KETU" -> "आध्यात्मिक प्रतिबिंब और अलगाव का समय है।"
            else -> ""
        }
        "mr" -> when (key) {
            "THEME_SUN" -> "ही आत्म-शोध आणि तुमची ओळख प्रस्थापित करण्याची वेळ आहे."
            "THEME_MOON" -> "भावनिक आरोग्य आणि मानसिक शांती सध्या प्राथमिक लक्ष आहे."
            "THEME_MARS" -> "क्रियाकलाप आणि महत्त्वाकांक्षा जास्त आहे. तुम्हाला खूप उत्साही वाटेल."
            "THEME_MERCURY" -> "संवाद आणि बुद्धिमत्ता सध्या तुमचे सर्वात मोठे सहाय्यक आहेत."
            "THEME_JUPITER" -> "विस्तार आणि शहाणपणाला पाठिंबा मिळत आहे."
            "THEME_VENUS" -> "नातेसंबंध आणि सर्जनशीलता सध्या केंद्रस्थानी आहेत."
            "THEME_SATURN" -> "शिस्त आणि दीर्घकालीन जबाबदारीची मागणी केली जात आहे."
            "THEME_RAHU" -> "अचानक बदल आणि तीव्र इच्छा उदयाला येऊ शकतात."
            "THEME_KETU" -> "आध्यात्मिक प्रतिबिंब आणि अलिप्ततेची वेळ आहे."
            else -> ""
        }
        else -> when (key) {
            "THEME_SUN" -> "This is a period of self-discovery and establishing your core identity."
            "THEME_MOON" -> "Emotional well-being and mental peace are the primary focus now."
            "THEME_MARS" -> "Activity and ambition are high. You'll feel quite driven."
            "THEME_MERCURY" -> "Communication and intellect are your greatest allies right now."
            "THEME_JUPITER" -> "Expansion and wisdom are supported."
            "THEME_VENUS" -> "Relationships and creativity are in focus."
            "THEME_SATURN" -> "Discipline and long-term responsibility are being demanded."
            "THEME_RAHU" -> "Sudden changes and intense ambitions may emerge."
            "THEME_KETU" -> "A time for spiritual reflection and detachment."
            else -> ""
        }
    }

    private fun getPlacementText(key: String, lang: String): String = when (lang) {
        "hi" -> when {
            key.endsWith("_HOUSE_1") -> "आप अपनी व्यक्तिगत जीवन शक्ति पर ध्यान केंद्रित करेंगे।"
            key.endsWith("_HOUSE_2") -> "पारिवारिक मूल्य और सुरक्षा सक्रिय हो रहे हैं।"
            key.endsWith("_HOUSE_10") -> "करियर और समाज में आपका योगदान मुख्य फोकस है।"
            else -> "जीवन के एक विशिष्ट क्षेत्र में सक्रियता बनी हुई है।"
        }
        "mr" -> when {
            key.endsWith("_HOUSE_1") -> "तुम्ही तुमच्या वैयक्तिक चैतन्यशक्तीवर लक्ष केंद्रित कराल."
            key.endsWith("_HOUSE_2") -> "कौटुंबिक मूल्ये आणि सुरक्षा सक्रिय होत आहेत."
            key.endsWith("_HOUSE_10") -> "करिअर आणि समाजातील तुमचे योगदान मुख्य लक्ष आहे."
            else -> "जीवनाच्या एका विशिष्ट क्षेत्रात सक्रियता आहे."
        }
        else -> when {
            key.endsWith("_HOUSE_1") -> "Focus will be on your personal vitality and self-projection."
            key.endsWith("_HOUSE_2") -> "Foundational security and values are being activated."
            key.endsWith("_HOUSE_10") -> "Career and your contribution to society are the primary focus."
            else -> "A specific area of your life is seeing significant activity."
        }
    }

    private fun getAxisText(key: String, lang: String): String = when (lang) {
        "hi" -> when (key) {
            "AXIS_5", "AXIS_9" -> "वर्तमान समय सहज प्रगति का संकेत देता है।"
            "AXIS_6", "AXIS_8" -> "इस समय आंतरिक संघर्ष के लिए धैर्य की आवश्यकता है।"
            else -> ""
        }
        "mr" -> when (key) {
            "AXIS_5", "AXIS_9" -> "सध्याची वेळ सुलभ प्रगती दर्शवते."
            "AXIS_6", "AXIS_8" -> "यावेळी अंतर्गत संघर्षासाठी संयम आवश्यक आहे."
            else -> ""
        }
        else -> when (key) {
            "AXIS_5", "AXIS_9" -> "The current alignment indicates smooth progress."
            "AXIS_6", "AXIS_8" -> "Internal friction may arise, requiring extra patience."
            else -> ""
        }
    }

    private fun getTransitText(key: String, lang: String): String = when (lang) {
        "hi" -> when {
            key.startsWith("TRANSIT_JUPITER") -> "बृहस्पति का गोचर आपके विस्तार का समर्थन कर रहा है।"
            key.startsWith("TRANSIT_SATURN") -> "एक महत्वपूर्ण संरचनात्मक बदलाव चल रहा है।"
            else -> ""
        }
        "mr" -> when {
            key.startsWith("TRANSIT_JUPITER") -> "गुरूचे गोचर तुमच्या विस्ताराला समर्थन देत आहे."
            key.startsWith("TRANSIT_SATURN") -> "एक महत्त्वाचा रचनात्मक बदल सुरू आहे."
            else -> ""
        }
        else -> when {
            key.startsWith("TRANSIT_JUPITER") -> "A supportive transit is assisting your growth."
            key.startsWith("TRANSIT_SATURN") -> "A significant structural shift is underway."
            else -> ""
        }
    }

    private fun getYogaText(key: String, lang: String): String = when (lang) {
        "hi" -> when (key) {
            "YOGA_GAJA_KESARI" -> "एक सुरक्षात्मक प्रभाव आपको स्थिरता प्रदान कर रहा है।"
            "YOGA_RAJA_INDICATOR" -> "एक शक्तिशाली सफलता-केंद्रित प्रभाव आपके पथ पर सक्रिय है।"
            "YOGA_DHANA_INDICATOR" -> "वित्तीय समृद्धि के सकारात्मक संकेत मिल रहे हैं।"
            else -> ""
        }
        "mr" -> when (key) {
            "YOGA_GAJA_KESARI" -> "एक संरक्षणात्मक प्रभाव तुम्हाला स्थिरता देत आहे."
            "YOGA_RAJA_INDICATOR" -> "तुमच्या मार्गावर एक शक्तिशाली यश-केंद्रित प्रभाव सक्रिय आहे."
            "YOGA_DHANA_INDICATOR" -> "आर्थिक समृद्धीचे सकारात्मक संकेत मिळत आहेत."
            else -> ""
        }
        else -> when (key) {
            "YOGA_GAJA_KESARI" -> "A protective influence is granting you stability."
            "YOGA_RAJA_INDICATOR" -> "A powerful success-oriented influence is active on your path."
            "YOGA_DHANA_INDICATOR" -> "Positive indicators for financial prosperity are emerging."
            else -> ""
        }
    }

    private fun getElaborateClosing(lang: String) = when (lang) {
        "hi" -> "अधिक गहराई से देखने पर, यह अवधि बताती है कि आपके कार्य आपके दीर्घकालिक लक्ष्यों से भारी रूप से प्रभावित हैं। अपनी तत्काल जरूरतों को अपने पथ की संरचनात्मक आवश्यकताओं के साथ संतुलित करना महत्वपूर्ण है।"
        "mr" -> "अधिक खोलवर पाहिल्यास, हा काळ सूचित करतो की तुमच्या कृतींवर तुमच्या दीर्घकालीन ध्येयांचा मोठा प्रभाव आहे. तुमच्या तातडीच्या गरजा तुमच्या मार्गाच्या रचनात्मक गरजांशी संतुलित करणे महत्त्वाचे आहे."
        else -> "Looking deeper, this period suggests that your actions are heavily influenced by your long-term goals. It's important to balance your immediate needs with the structural requirements of your path."
    }
}
