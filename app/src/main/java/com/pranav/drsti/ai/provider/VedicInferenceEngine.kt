package com.pranav.drsti.ai.provider

import com.pranav.drsti.model.*

/**
 * Deterministic Expert System for Vedic Jyotish Interpretation.
 * Codifies laws from BPHS, Phaladeepika, and Saravali into human-friendly reasoning.
 */
object VedicInferenceEngine {

    /**
     * Synthesizes a human-friendly "Reasoning Digest" from raw Jyotish data.
     */
    fun synthesize(
        context: AiRequestContext,
        detailLevel: DetailLevel = DetailLevel.SUMMARY,
        language: String = "en"
    ): String {
        val facts = extractFacts(context)
        val interpretations = matchLaws(facts, context, language)
        
        return aggregate(interpretations, detailLevel, language)
    }

    private data class JyotishFact(
        val planet: PlanetName,
        val house: Int,
        val sign: ZodiacSign,
        val isRetrograde: Boolean,
        val isDashaLord: Boolean = false,
        val isAntardashaLord: Boolean = false,
        val natalHouse: Int = house // House relative to Lagna
    )

    private data class LawResult(
        val category: String,
        val intensity: Int, // 1-100
        val text: String,
        val supportDelta: Int = 0 // Impact on support score
    )

    private fun extractFacts(context: AiRequestContext): List<JyotishFact> {
        val kundali = context.kundali ?: return emptyList()
        val dasha = context.dasha
        
        return kundali.planets.map { p ->
            JyotishFact(
                planet = p.planet,
                house = p.house,
                sign = p.sign,
                isRetrograde = p.retrograde,
                isDashaLord = dasha?.currentMahadasha?.planet == p.planet,
                isAntardashaLord = dasha?.currentAntardasha?.planet == p.planet
            )
        }
    }

    private fun matchLaws(facts: List<JyotishFact>, context: AiRequestContext, language: String): List<LawResult> {
        val results = mutableListOf<LawResult>()
        val md = facts.firstOrNull { it.isDashaLord }
        val ad = facts.firstOrNull { it.isAntardashaLord }
        
        // 1. Dasha Lord Theme
        md?.let { boss ->
            results.add(LawResult(
                category = "OVERARCHING_THEME",
                intensity = 80,
                text = getDashaLordTheme(boss.planet, language)
            ))
            
            results.add(LawResult(
                category = "DASHA_FOCUS",
                intensity = 70,
                text = getHouseTheme(boss.house, language)
            ))
        }

        // 2. MD/AD Axis Logic
        if (md != null && ad != null && md.planet != ad.planet) {
            val axis = calculateAxis(md.house, ad.house)
            val axisText = getAxisInterpretation(axis, language)
            if (axisText.isNotEmpty()) {
                results.add(LawResult(
                    category = "AXIS_RELATIONSHIP",
                    intensity = 60,
                    text = axisText,
                    supportDelta = if (axis in listOf(5, 9, 3, 11)) 15 else if (axis in listOf(6, 8, 2, 12)) -15 else 0
                ))
            }
        }

        // 3. Transit (Gochara) Triggers
        context.planetaryPositions?.positions?.forEach { transit ->
            val natalMoon = context.kundali?.planets?.firstOrNull { it.planet == PlanetName.MOON }
            if (natalMoon != null) {
                // House from Moon: (transit sign - moon sign + 12) % 12 + 1
                val houseFromMoon = ((transit.sign.index - natalMoon.sign.index + 12) % 12) + 1
                val transitEffect = getTransitEffect(transit.planet, houseFromMoon, language)
                if (transitEffect.isNotEmpty()) {
                    results.add(LawResult(
                        category = "TRANSIT_TRIGGER",
                        intensity = 50,
                        text = transitEffect
                    ))
                }
            }
        }

        // 4. Basic Yoga Detection
        if (detectGajaKesari(facts, context)) {
            results.add(LawResult(
                category = "YOGA",
                intensity = 90,
                text = getYogaText("GAJA_KESARI", language),
                supportDelta = 20
            ))
        }

        return results
    }

    private fun calculateAxis(h1: Int, h2: Int): Int {
        val diff = (h2 - h1 + 12) % 12
        return if (diff == 0) 1 else diff + 1
    }

    private fun getAxisInterpretation(axis: Int, lang: String): String = when (lang) {
        "hi" -> when (axis) {
            5, 9 -> "मुख्य और उप-अवधि के बीच एक सामंजस्यपूर्ण संबंध है, जो सहज प्रगति का संकेत देता है।"
            6, 8 -> "इस समय आंतरिक संघर्ष या अप्रत्याशित बाधाएं आ सकती हैं, जिनके लिए धैर्य की आवश्यकता है।"
            3, 11 -> "यह अवधि सामाजिक नेटवर्क और व्यक्तिगत प्रयासों के माध्यम से लाभ के लिए उत्कृष्ट है।"
            2, 12 -> "ऊर्जा या संसाधनों का थोड़ा क्षय हो सकता है; सतर्क नियोजन की सलाह दी जाती है।"
            else -> ""
        }
        "mr" -> when (axis) {
            5, 9 -> "मुख्य आणि उप-काळामध्ये एक सुसंवादी संबंध आहे, जो सुलभ प्रगती दर्शवतो."
            6, 8 -> "यावेळी अंतर्गत संघर्ष किंवा अनपेक्षित अडथळे येऊ शकतात, ज्यासाठी संयम आवश्यक आहे."
            3, 11 -> "हा काळ सामाजिक नेटवर्क आणि वैयक्तिक प्रयत्नांतून फायद्यासाठी उत्कृष्ट आहे."
            2, 12 -> "ऊर्जा किंवा संसाधनांचा थोडासा ऱ्हास होऊ शकतो; सतर्क नियोजनाचा सल्ला दिला जातो."
            else -> ""
        }
        else -> when (axis) {
            5, 9 -> "There is a harmonious relationship between the major and sub-periods, indicating smooth progress."
            6, 8 -> "Internal friction or unexpected hurdles may arise at this time, requiring patience."
            3, 11 -> "This period is excellent for gains through social networks and personal efforts."
            2, 12 -> "There might be a slight drain of energy or resources; cautious planning is advised."
            else -> ""
        }
    }

    private fun getTransitEffect(planet: PlanetName, houseFromMoon: Int, lang: String): String = when (lang) {
        "hi" -> when {
            planet == PlanetName.JUPITER && houseFromMoon in listOf(2, 5, 7, 9, 11) -> "बृहस्पति का वर्तमान गोचर आपके मानसिक और आध्यात्मिक विस्तार का समर्थन कर रहा है।"
            planet == PlanetName.SATURN && houseFromMoon in listOf(1, 12, 2) -> "शनि का प्रभाव वर्तमान में संरचनात्मक परिवर्तन और मानसिक परिपक्वता की मांग कर रहा है।"
            else -> ""
        }
        "mr" -> when {
            planet == PlanetName.JUPITER && houseFromMoon in listOf(2, 5, 7, 9, 11) -> "गुरूचे सध्याचे गोचर तुमच्या मानसिक आणि आध्यात्मिक विस्ताराला समर्थन देत आहे."
            planet == PlanetName.SATURN && houseFromMoon in listOf(1, 12, 2) -> "शनीचा प्रभाव सध्या रचनात्मक बदल आणि मानसिक परिपक्वतेची मागणी करत आहे."
            else -> ""
        }
        else -> when {
            planet == PlanetName.JUPITER && houseFromMoon in listOf(2, 5, 7, 9, 11) -> "The current movement of Jupiter is supporting your mental and spiritual expansion."
            planet == PlanetName.SATURN && houseFromMoon in listOf(1, 12, 2) -> "A significant structural shift is underway, demanding maturity and long-term focus."
            else -> ""
        }
    }

    private fun detectGajaKesari(facts: List<JyotishFact>, context: AiRequestContext): Boolean {
        val jupiter = facts.firstOrNull { it.planet == PlanetName.JUPITER }
        val moon = facts.firstOrNull { it.planet == PlanetName.MOON }
        if (jupiter == null || moon == null) return false
        val dist = calculateAxis(moon.house, jupiter.house)
        return dist in listOf(1, 4, 7, 10)
    }

    private fun getYogaText(yoga: String, lang: String): String = when (lang) {
        "hi" -> when (yoga) {
            "GAJA_KESARI" -> "आपके चार्ट में एक शक्तिशाली सुरक्षात्मक योग सक्रिय है, जो बुद्धि और स्थिरता प्रदान करता है।"
            else -> ""
        }
        "mr" -> when (yoga) {
            "GAJA_KESARI" -> "तुमच्या चार्टमध्ये एक शक्तिशाली संरक्षणात्मक योग सक्रिय आहे, जो बुद्धिमत्ता आणि स्थिरता प्रदान करतो."
            else -> ""
        }
        else -> when (yoga) {
            "GAJA_KESARI" -> "A powerful protective influence is active in your chart, granting wisdom and stability."
            else -> ""
        }
    }

    private fun getDashaLordTheme(planet: PlanetName, lang: String): String = when (lang) {
        "hi" -> when (planet) {
            PlanetName.SUN -> "यह आत्म-खोज, अधिकार और अपनी मुख्य पहचान स्थापित करने का समय है। अपनी व्यक्तिगत शक्ति और नेतृत्व पर ध्यान दें।"
            PlanetName.MOON -> "भावनात्मक कल्याण, घरेलू जीवन और आपकी आंतरिक मानसिक स्थिति अभी प्राथमिक फोकस हैं। यह पोषण और ग्रहणशीलता का समय है।"
            PlanetName.MARS -> "गतिविधि, महत्वाकांक्षा और अभियान उच्च हैं। आप प्रतिस्पर्धा करने, जोखिम उठाने और अपने हितों की रक्षा करने का तीव्र आग्रह महसूस कर सकते हैं।"
            PlanetName.MERCURY -> "संचार, बुद्धि और विश्लेषणात्मक कौशल बढ़ गए हैं। सीखने, व्यवसाय और नेटवर्किंग के लिए एक अच्छा समय है।"
            PlanetName.JUPITER -> "विस्तार, ज्ञान और विकास समर्थित हैं। यह अवधि उच्च शिक्षा, आध्यात्मिक गतिविधियों और प्रचुरता के पक्ष में है।"
            PlanetName.VENUS -> "रिश्ते, रचनात्मकता और भौतिक सुख-सुविधाएं फोकस में हैं। सद्भाव, सुंदरता और आनंद खोजने का समय है।"
            PlanetName.SATURN -> "अनुशासन, संरचना और दीर्घकालिक जिम्मेदारी की आवश्यकता है। प्रगति धीमी लग सकती है, लेकिन यह एक ठोस नींव बना रही है।"
            PlanetName.RAHU -> "अचानक परिवर्तन, सांसारिक इच्छाएं और अपरंपरागत रास्ते उभर सकते हैं। तीव्र महत्वाकांक्षाओं के बीच स्पष्टता पर ध्यान दें।"
            PlanetName.KETU -> "आंतरीकरण, वैराग्य और आध्यात्मिक प्रतिबिंब उजागर होते हैं। यह अतीत को छोड़ने और भीतर देखने का समय है।"
        }
        "mr" -> when (planet) {
            PlanetName.SUN -> "ही आत्म-शोध, अधिकार आणि तुमची मुख्य ओळख प्रस्थापित करण्याची वेळ आहे. तुमच्या वैयक्तिक शक्तीवर आणि नेतृत्वावर लक्ष केंद्रित करा."
            PlanetName.MOON -> "भावनिक आरोग्य, घरगुती जीवन आणि तुमची आंतरिक मानसिक स्थिती सध्या प्राथमिक लक्ष केंद्रित आहे. ही संगोपन आणि ग्रहणक्षमतेची वेळ आहे."
            PlanetName.MARS -> "क्रियाकलाप, महत्त्वाकांक्षा आणि उत्साह जास्त आहे. तुम्हाला स्पर्धा करण्याची, जोखीम पत्करण्याची आणि तुमच्या हितसंबंधांचे रक्षण करण्याची तीव्र इच्छा वाटू शकते."
            PlanetName.MERCURY -> "संवाद, बुद्धी आणि विश्लेषणात्मक कौशल्ये वाढली आहेत. शिकण्यासाठी, व्यवसायासाठी आणि नेटवर्किंगसाठी चांगली वेळ आहे."
            PlanetName.JUPITER -> "विस्तार, शहाणपण आणि वाढीला पाठिंबा आहे. हा काळ उच्च शिक्षण, आध्यात्मिक प्रयत्न आणि समृद्धीसाठी अनुकूल आहे."
            PlanetName.VENUS -> "नातेसंबंध, सर्जनशीलता आणि भौतिक सुखसोयी केंद्रस्थानी आहेत. सुसंवाद, सौंदर्य आणि आनंद शोधण्याची वेळ आहे."
            PlanetName.SATURN -> "शिस्त, रचना आणि दीर्घकालीन जबाबदारी आवश्यक आहे. प्रगती संथ वाटू शकते, परंतु ती एक भक्कम पाया रचत आहे."
            PlanetName.RAHU -> "अचानक बदल, सांसारिक इच्छा आणि अपारंपरिक मार्ग उदयाला येऊ शकतात. तीव्र महत्त्वाकांक्षेमध्ये स्पष्टतेवर लक्ष केंद्रित करा."
            PlanetName.KETU -> "अंतर्मुखता, अलिप्तता आणि आध्यात्मिक प्रतिबिंब ठळकपणे मांडले आहेत. ही भूतकाळ सोडून देण्याची आणि आत पाहण्याची वेळ आहे."
        }
        else -> when (planet) {
            PlanetName.SUN -> "This is a period of self-discovery, authority, and establishing your core identity. Focus on your personal power and leadership."
            PlanetName.MOON -> "Emotional well-being, home life, and your inner mental state are the primary focus now. It's a time for nurturing and receptivity."
            PlanetName.MARS -> "Activity, ambition, and drive are high. You may feel a strong urge to compete, take risks, and protect your interests."
            PlanetName.MERCURY -> "Communication, intellect, and analytical skills are heightened. A great time for learning, business, and networking."
            PlanetName.JUPITER -> "Expansion, wisdom, and growth are supported. This period favors higher learning, spiritual pursuits, and abundance."
            PlanetName.VENUS -> "Relationships, creativity, and material comfort are in focus. A time for seeking harmony, beauty, and pleasure."
            PlanetName.SATURN -> "Discipline, structure, and long-term responsibility are required. Progress may feel slow, but it's building a solid foundation."
            PlanetName.RAHU -> "Sudden changes, worldly desires, and unconventional paths may emerge. Focus on clarity amidst intense ambitions."
            PlanetName.KETU -> "Internalization, detachment, and spiritual reflection are highlighted. It's a time to let go of the past and look within."
        }
    }

    private fun getHouseTheme(house: Int, lang: String): String = when (lang) {
        "hi" -> when (house) {
            1 -> "आपकी व्यक्तिगत जीवन शक्ति और आप दुनिया के सामने खुद को कैसे पेश करते हैं, यह क्रिया का मुख्य क्षेत्र है।"
            2 -> "वित्त, पारिवारिक मूल्य और आपकी मूलभूत सुरक्षा सक्रिय हो रही है।"
            3 -> "संचार, भाई-बहन और आपके व्यक्तिगत कौशल विकास के प्रमुख क्षेत्र हैं।"
            4 -> "घर, भावनात्मक नींव और आंतरिक शांति वह जगह है जहाँ आप सबसे अधिक गतिविधि पाएंगे।"
            5 -> "रचनात्मकता, बच्चे और आपकी बौद्धिक खोज उजागर होती हैं।"
            6 -> "दैनिक कार्य, स्वास्थ्य और निरंतर प्रयास के माध्यम से बाधाओं को दूर करना आवश्यक है।"
            7 -> "साझेदारी, रिश्ते और जनता के साथ आपकी बातचीत फोकस में हैं।"
            8 -> "गहन परिवर्तन, साझा संसाधन और अज्ञात में शोध समर्थित हैं।"
            9 -> "उच्च ज्ञान, लंबी दूरी की यात्रा और आपकी विश्वास प्रणालियों का विस्तार हो रहा है।"
            10 -> "करियर, सार्वजनिक स्थिति और समाज में आपका योगदान प्राथमिक फोकस है।"
            11 -> "सामाजिक नेटवर्क, लाभ और अपने दीर्घकालिक सपनों को पूरा करना सक्रिय विषय हैं।"
            12 -> "अवचेतन प्रक्रियाएं, अलगाव और आध्यात्मिक मुक्ति अंतर्निहित धाराएं हैं।"
            else -> ""
        }
        "mr" -> when (house) {
            1 -> "तुमची वैयक्तिक चैतन्यशक्ती आणि तुम्ही जगासमोर स्वतःला कसे सादर करता हे कृतीचे मुख्य क्षेत्र आहे."
            2 -> "वित्त, कौटुंबिक मूल्ये आणि तुमची पायाभूत सुरक्षा सक्रिय होत आहे."
            3 -> "संवाद, भावंडे आणि तुमची वैयक्तिक कौशल्ये ही वाढीसाठी महत्त्वाची क्षेत्रे आहेत."
            4 -> "घर, भावनिक पाया आणि आंतरिक शांतता येथे तुम्हाला सर्वात जास्त हालचाल आढळेल."
            5 -> "सर्जनशीलता, मुले आणि तुमचे बौद्धिक प्रयत्न ठळकपणे मांडले आहेत."
            6 -> "दैनिक काम, आरोग्य आणि सततच्या प्रयत्नातून अडथळ्यांवर मात करणे आवश्यक आहे."
            7 -> "भागीदारी, नातेसंबंध आणि लोकांशी तुमचा संवाद केंद्रस्थानी आहे."
            8 -> "सखोल परिवर्तन, सामायिक संसाधने आणि अज्ञाताचा शोध घेण्यास पाठिंबा आहे."
            9 -> "उच्च शहाणपण, लांब पल्ल्याचा प्रवास आणि तुमच्या श्रद्धा प्रणालींचा विस्तार होत आहे."
            10 -> "करिअर, सार्वजनिक दर्जा आणि समाजातील तुमचे योगदान हे प्राथमिक लक्ष आहे."
            11 -> "सोशल नेटवर्क्स, नफा आणि तुमची दीर्घकालीन स्वप्ने पूर्ण करणे ही सक्रिय थीम आहे."
            12 -> "सुप्त मन प्रक्रिया, विलगीकरण आणि आध्यात्मिक मुक्ती हे अंतर्निहित प्रवाह आहेत."
            else -> ""
        }
        else -> when (house) {
            1 -> "Your personal vitality and how you project yourself to the world is the main arena of action."
            2 -> "Finances, family values, and your foundational security are being activated."
            3 -> "Communication, siblings, and your personal skills are the key areas for growth."
            4 -> "Home, emotional foundations, and inner peace are where you will find the most activity."
            5 -> "Creativity, children, and your intellectual pursuits are highlighted."
            6 -> "Daily work, health, and overcoming obstacles through persistent effort are necessary."
            7 -> "Partnerships, relationships, and your interactions with the public are in focus."
            8 -> "Deep transformation, shared resources, and research into the unknown are supported."
            9 -> "Higher wisdom, long-distance travel, and your belief systems are being expanded."
            10 -> "Career, public status, and your contribution to society are the primary focus."
            11 -> "Social networks, gains, and fulfilling your long-term dreams are active themes."
            12 -> "Subconscious processes, isolation, and spiritual liberation are the underlying currents."
            else -> ""
        }
    }

    private fun aggregate(results: List<LawResult>, detailLevel: DetailLevel, lang: String): String {
        if (results.isEmpty()) return when (lang) {
            "hi" -> "मैं आपका चार्ट देख रहा हूँ, लेकिन विशिष्ट व्याख्या प्रदान करने के लिए मुझे अधिक विवरण चाहिए।"
            "mr" -> "मी तुमचा तक्ता पाहत आहे, परंतु विशिष्ट स्पष्टीकरण देण्यासाठी मला अधिक तपशीलांची आवश्यकता आहे."
            else -> "I'm looking at your chart, but I need more details to provide a specific interpretation."
        }
        
        val sb = StringBuilder()
        
        results.find { it.category == "OVERARCHING_THEME" }?.let {
            sb.append(it.text).append(" ")
        }
        
        results.find { it.category == "DASHA_FOCUS" }?.let {
            sb.append(it.text)
        }
        
        if (detailLevel == DetailLevel.ELABORATE) {
            val elaborateText = when (lang) {
                "hi" -> "\n\nअधिक गहराई से देखने पर, यह अवधि बताती है कि आपके कार्य आपके दीर्घकालिक लक्ष्यों से भारी रूप से प्रभावित हैं। अपनी तत्काल जरूरतों को अपने पथ की संरचनात्मक आवश्यकताओं के साथ संतुलित करना महत्वपूर्ण है।"
                "mr" -> "\n\nअधिक खोलवर पाहिल्यास, हा काळ सूचित करतो की तुमच्या कृतींवर तुमच्या दीर्घकालीन ध्येयांचा मोठा प्रभाव आहे. तुमच्या तातडीच्या गरजा तुमच्या मार्गाच्या रचनात्मक गरजांशी संतुलित करणे महत्त्वाचे आहे."
                else -> "\n\nLooking deeper, this period suggests that your actions are heavily influenced by your long-term goals. It's important to balance your immediate needs with the structural requirements of your path."
            }
            sb.append(elaborateText)
        }
        
        return sb.toString()
    }
}
