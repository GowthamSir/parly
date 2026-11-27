package com.example.data.model

object PairlyCatalog {

    val stateToCities: LinkedHashMap<String, List<String>> = linkedMapOf(
        "Maharashtra" to listOf("Mumbai", "Pune", "Nagpur", "Nashik", "Thane"),
        "Karnataka" to listOf("Bengaluru", "Mysuru", "Mangaluru", "Hubballi"),
        "Delhi NCR" to listOf("New Delhi", "Gurugram", "Noida", "Faridabad"),
        "Telangana" to listOf("Hyderabad", "Warangal", "Secunderabad"),
        "Tamil Nadu" to listOf("Chennai", "Coimbatore", "Madurai", "Pondicherry"),
        "West Bengal" to listOf("Kolkata", "Siliguri", "Howrah"),
        "Kerala" to listOf("Kochi", "Thiruvananthapuram", "Kozhikode"),
        "Gujarat" to listOf("Ahmedabad", "Surat", "Vadodara", "Rajkot"),
        "Rajasthan" to listOf("Jaipur", "Udaipur", "Jodhpur"),
        "Goa" to listOf("Panaji", "Margao", "Anjuna"),
        "Punjab & Chandigarh" to listOf("Chandigarh", "Amritsar", "Ludhiana"),
        "Uttar Pradesh" to listOf("Lucknow", "Varanasi", "Agra", "Kanpur"),
        "Madhya Pradesh" to listOf("Indore", "Bhopal"),
        "Assam" to listOf("Guwahati"),
        "Odisha" to listOf("Bhubaneswar", "Puri")
    )

    val allStates: List<String> = stateToCities.keys.toList()

    fun citiesForState(state: String): List<String> =
        stateToCities[state] ?: emptyList()

    val interests: List<String> = listOf(
        "Indie Cinema", "Filter Coffee", "Queer Lit", "Pride Arts",
        "Design", "Music", "Travel", "Fitness", "Tech", "Foodie",
        "Stand-up Comedy", "Theatre", "Architecture", "Museums",
        "Startups", "Fashion", "Vinyl & Jazz", "Mindfulness"
    )

    val hobbies: List<String> = listOf(
        "Photography", "Cooking", "Cycling", "Sketching",
        "Hiking", "Running", "Reading", "Gaming",
        "Dancing", "Blogging", "Plant Parent", "Yoga",
        "Guitar", "Board Games", "Baking", "Swimming"
    )

    val languages: List<String> = listOf(
        "English", "Hindi", "Marathi", "Kannada", "Tamil",
        "Telugu", "Malayalam", "Bengali", "Gujarati",
        "Punjabi", "Urdu", "Konkani", "Odia", "Assamese"
    )

    val promptQuestions: List<String> = listOf(
        "My ideal Sunday in the city looks like...",
        "Two truths and a lie about me",
        "A queer Indian film, book, or song I adore",
        "The quickest way to win me over",
        "Where you'll find me after 8 PM",
        "A green flag I always look for",
        "Something I'm genuinely nerdy about"
    )
}
