package com.pranav.drsti.ai.provider

import com.pranav.drsti.model.*

/**
 * Renders structured Jyotish findings into human-friendly natural language.
 * Completely filters technical jargon. This logic serves as the "source of truth"
 * for the local AI's reasoning and final output structure.
 */
object AstroInterpretationRenderer {

    fun render(
        findings: List<JyotishFinding>,
        detailLevel: DetailLevel,
        language: String
    ): String {
        if (findings.isEmpty()) return getEmptyStateText(language)
        
        val sb = StringBuilder()
        
        // Orchestrated Delivery: Intro -> Theme -> Placement -> Axis -> Transits -> Yogas -> Closing
        sb.append(getIntro(language)).append(" ")
        
        // 1. Theme (Overarching vibe)
        findings.find { it.category == FindingCategory.THEME }?.let { 
            sb.append(getThemeText(it.key, language)).append(" ") 
        }
        
        // 2. Placement (Area of life focus)
        findings.find { it.category == FindingCategory.PLACEMENT }?.let { 
            sb.append(getPlacementText(it.key, language)).append(" ") 
        }
        
        // 3. Axis (MD/AD relationship)
        findings.find { it.category == FindingCategory.AXIS }?.let { 
            sb.append(getAxisText(it.key, language)).append(" ") 
        }
        
        // 4. Notable Transits
        val transits = findings.filter { it.category == FindingCategory.TRANSIT }
        if (transits.isNotEmpty()) {
            transits.forEach { sb.append(getTransitText(it.key, language)).append(" ") }
        }
        
        // 5. Yogas
        findings.find { it.category == FindingCategory.YOGA }?.let { 
            sb.append(getYogaText(it.key, language)).append(" ") 
        }

        if (detailLevel == DetailLevel.ELABORATE) {
            sb.append("\n\n").append(getElaborateClosing(language))
        }

        return sb.toString().trim()
    }

    /**
     * Performs Kotlin-based logic and calculations based on tags provided by the AI Dispatcher.
     * This is the "Raw Interpretation" phase.
     */
    fun renderFactsFromTags(tags: String, context: AiRequestContext): String {
        val sb = StringBuilder()
        val t = tags.uppercase()
        val kundali = context.kundali
        val dasha = context.dasha

        // 1. Basic Identity
        if (t.contains("MOON_SIGN") || t.contains("IDENTITY") || t.contains("RASHI")) {
            kundali?.let { k ->
                val moonSign = k.planets.find { it.planet == PlanetName.MOON }?.sign?.displayName ?: "Unknown"
                sb.append("Vedic Identity: Your Rashi (Moon Sign) is $moonSign. Your Lagna is ${k.ascendant.sign.displayName}. ")
            }
        }
        
        // 2. Career & Status
        if (t.contains("CAREER") || t.contains("WORK") || t.contains("JOB")) {
            kundali?.let { k ->
                val h10 = k.planets.filter { it.house == 10 }.joinToString { it.planet.name.toString() }
                sb.append("Career (10th House): ${if(h10.isBlank()) "No planets currently here" else "$h10 is present"}. ")
            }
        }
        
        // 3. Relationships
        if (t.contains("ROMANCE") || t.contains("MARRIAGE") || t.contains("PARTNER")) {
            kundali?.let { k ->
                val h7 = k.planets.filter { it.house == 7 }.joinToString { it.planet.name.toString() }
                sb.append("Partnerships (7th House): ${if(h7.isBlank()) "Clear of major planetary occupants" else "Occupied by $h7"}. ")
            }
        }

        // 4. Current Timing (The "When")
        if (t.contains("TIMING") || t.contains("DASHA") || t.contains("FUTURE")) {
            dasha?.let { d ->
                sb.append("Current Cosmic Cycle: You are in ${d.currentMahadasha?.planet?.name} Mahadasha. This is a period of ${getPlanetTheme(d.currentMahadasha?.planet)}. ")
            }
        }

        // 5. Today's General Vibe
        if (t.contains("TODAY") || t.contains("PANCHANG") || t.contains("VIBE")) {
            context.panchang?.let { p ->
                sb.append("Today's Environment: ${p.tithiName} Tithi, which is generally ${if(p.paksha.contains("Shukla")) "growing and supportive" else "internal and reflective"}. ")
            } ?: sb.append("Today's Environment: The cosmic energy is currently transitioning. ")
        }
        
        return sb.toString().trim()
    }

    private fun getPlanetTheme(planet: PlanetName?) = when (planet) {
        PlanetName.SUN -> "soul-searching and leadership"
        PlanetName.MOON -> "emotional focus and peace"
        PlanetName.MARS -> "energy and ambition"
        PlanetName.MERCURY -> "communication and logic"
        PlanetName.JUPITER -> "expansion and luck"
        PlanetName.VENUS -> "creativity and comfort"
        PlanetName.SATURN -> "discipline and patience"
        PlanetName.RAHU -> "desire and change"
        PlanetName.KETU -> "detachment and spirituality"
        else -> "general life events"
    }

    /**
     * Provides a map of astrological facts indexed by tags.
     * This allows the AI Dispatcher to request only what it needs.
     */
    fun getKnowledgeBase(context: AiRequestContext): Map<String, String> {
        val facts = mutableMapOf<String, String>()
        
        context.kundali?.let { k ->
            val moonSign = k.planets.find { it.planet == PlanetName.MOON }?.sign?.displayName ?: "Unknown"
            facts["MOON_SIGN"] = "Your Moon sign (Rashi) is $moonSign."
            facts["LAGNA"] = "Your Ascendant (Lagna) is ${k.ascendant.sign.displayName}."
            
            val positions = k.planets.joinToString(", ") { "${it.planet.name} in ${it.sign.displayName} (House ${it.house})" }
            facts["PLANET_POSITIONS"] = "Planetary positions: $positions."
            
            // Add house-specific context for the AI
            facts["HOUSE_SIGNIFICATORS"] = """
                House meanings: 
                - House 1: Self, health, vitality.
                - House 2: Family, wealth, speech.
                - House 3: Siblings, efforts, communication.
                - House 4: Mother, home, inner peace.
                - House 5: Intelligence, children, creativity.
                - House 6: Challenges, health issues, service.
                - House 7: Partnerships, marriage, business.
                - House 8: Longevity, deep changes, legacy.
                - House 9: Fortune, higher wisdom, travel.
                - House 10: Career, status, public life.
                - House 11: Gains, friends, aspirations.
                - House 12: Solitude, expense, spiritual release.
            """.trimIndent()
        }
        
        context.dasha?.let { d ->
            facts["CURRENT_DASHA"] = "You are in ${d.currentMahadasha?.planet?.name} Mahadasha and ${d.currentAntardasha?.planet?.name} Antardasha."
        }
        
        context.panchang?.let { p ->
            facts["TODAY_PANCHANG"] = "Today's timing: ${p.tithiName} Tithi, ${p.nakshatra.displayName} Nakshatra, ${p.yogaName} Yoga."
        }
        
        return facts
    }

    /**
     * Provides the rules of this renderer as a string for inclusion in the AI prompt.
     */
    fun getRulesForPrompt(): String {
        return """
            INTERPRETATION RULES:
            1. Avoid technical jargon like "7th house", "Rahu in Aries", or "conjunction".
            2. Focus on practical implications: career, relationships, timing, and growth.
            3. Structure the response:
               - Start with a gentle introduction.
               - Describe the current "theme" or energy.
               - Highlight the specific area of life in focus (without naming house numbers).
               - Explain the timing/axis (smooth vs friction).
               - Mention any significant transits or protective influences (yogas).
            4. Provide an estimated "Astrological Support" percentage (0-100%) based on findings.
            5. Always conclude with the reminder that the final decision rests with the user.
        """.trimIndent()
    }

    private fun getIntro(lang: String) = when (lang) {
        "hi" -> "मैंने आपके चार्ट का सावधानीपूर्वक विश्लेषण किया है।"
        "mr" -> "मी तुमच्या कुंडलीचे काळजीपूर्वक विश्लेषण केले आहे."
        else -> "I have carefully analyzed your chart."
    }

    private fun getEmptyStateText(lang: String) = when (lang) {
        "hi" -> "मैं आपके चार्ट देख रहा हूँ, लेकिन विशिष्ट व्याख्या प्रदान करने के लिए मुझे अधिक विवरण चाहिए।"
        "mr" -> "मी तुमचा तक्ता पाहत आहे, परंतु विशिष्ट स्पष्टीकरण देण्यासाठी मला अधिक तपशीलांची आवश्यकता आहे।"
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
            else -> "एक नया अध्याय आपके जीवन में शुरू हो रहा है।"
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
            else -> "तुमच्या आयुष्यात एक नवीन अध्याय सुरू होत आहे."
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
            else -> "A new chapter is beginning in your life."
        }
    }

    private fun getPlacementText(key: String, lang: String): String = when (lang) {
        "hi" -> when {
            key.endsWith("_HOUSE_1") -> "आपका ध्यान अपने स्वयं के विकास और जीवन शक्ति पर होगा।"
            key.endsWith("_HOUSE_2") -> "पारिवारिक मूल्य और वित्तीय सुरक्षा अभी सक्रिय हो रहे हैं।"
            key.endsWith("_HOUSE_3") -> "छोटी यात्राएं और भाई-बहनों के साथ संबंध महत्वपूर्ण रहेंगे।"
            key.endsWith("_HOUSE_4") -> "घर और आंतरिक शांति पर ध्यान केंद्रित करने का समय है।"
            key.endsWith("_HOUSE_7") -> "साझेदारी और रिश्तों में संतुलन आवश्यक है।"
            key.endsWith("_HOUSE_9") -> "उच्च शिक्षा और आध्यात्मिक यात्रा का योग है।"
            key.endsWith("_HOUSE_10") -> "करियर और समाज में आपकी भूमिका मुख्य केंद्र है।"
            else -> "जीवन के एक विशिष्ट क्षेत्र में महत्वपूर्ण हलचल बनी हुई है।"
        }
        "mr" -> when {
            key.endsWith("_HOUSE_1") -> "तुमचे लक्ष तुमच्या स्वतःच्या विकासावर आणि चैतन्यशक्तीवर असेल."
            key.endsWith("_HOUSE_2") -> "कौटुंबिक मूल्ये आणि आर्थिक सुरक्षा सध्या सक्रिय होत आहेत."
            key.endsWith("_HOUSE_4") -> "घर आणि अंतर्गत शांततेवर लक्ष केंद्रित करण्याची वेळ आहे."
            key.endsWith("_HOUSE_7") -> "भागीदारी आणि नातेसंबंधांमध्ये संतुलन आवश्यक आहे."
            key.endsWith("_HOUSE_10") -> "करिअर आणि समाजातील तुमची भूमिका मुख्य केंद्र आहे."
            else -> "जीवनाच्या एका विशिष्ट क्षेत्रात महत्त्वाची हालचाल आहे."
        }
        else -> when {
            key.endsWith("_HOUSE_1") -> "Focus will be on your personal vitality and self-growth."
            key.endsWith("_HOUSE_2") -> "Foundational security and values are being activated."
            key.endsWith("_HOUSE_4") -> "Time to focus on home and inner stability."
            key.endsWith("_HOUSE_7") -> "Balance in partnerships and relationships is key."
            key.endsWith("_HOUSE_10") -> "Career and your contribution to society are the primary focus."
            else -> "A specific area of your life is seeing significant activity."
        }
    }

    private fun getAxisText(key: String, lang: String): String = when (lang) {
        "hi" -> when (key) {
            "AXIS_5", "AXIS_9" -> "वर्तमान समय सहज प्रगति और भाग्य का संकेत देता है।"
            "AXIS_6", "AXIS_8" -> "इस समय आंतरिक चुनौतियों के लिए धैर्य और सावधानी की आवश्यकता है।"
            else -> "परिवर्तन की धाराएं धीरे-धीरे बह रही हैं।"
        }
        "mr" -> when (key) {
            "AXIS_5", "AXIS_9" -> "सध्याची वेळ सुलभ प्रगती आणि भाग्याचे संकेत देते."
            "AXIS_6", "AXIS_8" -> "यावेळी अंतर्गत आव्हानांसाठी संयम आणि सावधगिरी आवश्यक आहे."
            else -> "बदलाचे प्रवाह हळूहळू वाहत आहेत."
        }
        else -> when (key) {
            "AXIS_5", "AXIS_9" -> "The current alignment indicates smooth progress and support."
            "AXIS_6", "AXIS_8" -> "Internal friction may arise, requiring extra patience and care."
            else -> "The currents of change are flowing steadily."
        }
    }

    private fun getTransitText(key: String, lang: String): String = when (lang) {
        "hi" -> when {
            key.startsWith("TRANSIT_JUPITER") -> "बृहस्पति का आशीर्वाद आपके विस्तार का समर्थन कर रहा है।"
            key.startsWith("TRANSIT_SATURN") -> "एक महत्वपूर्ण संरचनात्मक बदलाव और अनुशासन का समय चल रहा है।"
            else -> ""
        }
        "mr" -> when {
            key.startsWith("TRANSIT_JUPITER") -> "गुरूचे आशीर्वाद तुमच्या विस्ताराला समर्थन देत आहेत."
            key.startsWith("TRANSIT_SATURN") -> "एक महत्त्वाचा रचनात्मक बदल आणि शिस्तीची वेळ सुरू आहे."
            else -> ""
        }
        else -> when {
            key.startsWith("TRANSIT_JUPITER") -> "A supportive transit is assisting your long-term growth."
            key.startsWith("TRANSIT_SATURN") -> "A significant structural shift and period of discipline is underway."
            else -> ""
        }
    }

    private fun getYogaText(key: String, lang: String): String = when (lang) {
        "hi" -> when (key) {
            "YOGA_GAJA_KESARI" -> "एक सुरक्षात्मक प्रभाव आपको शक्ति और स्थिरता प्रदान कर रहा है।"
            "YOGA_RAJA_INDICATOR" -> "सफलता-केंद्रित ऊर्जा आपके पथ पर सक्रिय है।"
            "YOGA_DHANA_INDICATOR" -> "वित्तीय समृद्धि के सकारात्मक अवसर उभर रहे हैं।"
            else -> ""
        }
        "mr" -> when (key) {
            "YOGA_GAJA_KESARI" -> "एक संरक्षणात्मक प्रभाव तुम्हाला शक्ती आणि स्थिरता देत आहे."
            "YOGA_RAJA_INDICATOR" -> "यश-केंद्रित ऊर्जा तुमच्या मार्गावर सक्रिय आहे."
            "YOGA_DHANA_INDICATOR" -> "आर्थिक समृद्धीच्या सकारात्मक संधी निर्माण होत आहेत."
            else -> ""
        }
        else -> when (key) {
            "YOGA_GAJA_KESARI" -> "A protective influence is granting you wisdom and stability."
            "YOGA_RAJA_INDICATOR" -> "A success-oriented influence is active on your path."
            "YOGA_DHANA_INDICATOR" -> "Positive indicators for prosperity are emerging."
            else -> ""
        }
    }

    private fun getElaborateClosing(lang: String) = when (lang) {
        "hi" -> "कुल मिलाकर, यह अवधि बताती है कि आपके कार्य आपके दीर्घकालिक लक्ष्यों से गहराई से जुड़े हैं। अपनी तत्काल इच्छाओं को अपने जीवन की संरचनात्मक आवश्यकताओं के साथ संतुलित करना ही आपकी सफलता की कुंजी है।"
        "mr" -> "एकूणच, हा काळ सूचित करतो की तुमच्या कृती तुमच्या दीर्घकालीन ध्येयांशी खोलवर जोडलेल्या आहेत. तुमच्या तातडीच्या इच्छा तुमच्या आयुष्याच्या रचनात्मक गरजांशी संतुलित करणे हीच तुमच्या यशाची गुरुकिल्ली आहे."
        else -> "Overall, this period suggests that your actions are deeply aligned with your long-term goals. Balancing your immediate desires with the structural requirements of your path is the key to your success."
    }
}
