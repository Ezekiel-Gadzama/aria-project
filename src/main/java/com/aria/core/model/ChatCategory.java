package com.aria.core.model;

/**
 * Enumeration of all chat categories with descriptions to help OpenAI understand
 * when a conversation falls into each category.
 * 
 * Each category includes:
 * - name: The exact category name (used in database)
 * - description: Explanation of when a chat belongs to this category
 * - keywords: Common keywords/terms associated with this category
 */
public enum ChatCategory {
    // Romantic/Dating Categories
    DATING(
        "dating",
        "Conversations about going on dates, setting up romantic meetings, or expressing romantic interest. Includes topics like asking someone out, planning dates, discussing relationship intentions.",
        new String[]{"date", "go out", "coffee", "dinner", "movie", "together", "romantic interest"}
    ),
    FLIRTING(
        "flirting",
        "Playful, suggestive, or teasing conversations with romantic or sexual undertones. Includes compliments, playful banter, suggestive comments, or innuendos.",
        new String[]{"cute", "hot", "beautiful", "handsome", "flirting", "teasing", "wink", "kiss"}
    ),
    ROMANCE(
        "romance",
        "Conversations with romantic themes, expressing love, affection, or romantic feelings. More serious than flirting, includes love expressions, romantic gestures, or relationship building.",
        new String[]{"love", "romance", "affection", "sweetheart", "darling", "relationship", "feelings"}
    ),
    RELATIONSHIP(
        "relationship",
        "Conversations about being in a relationship, discussing relationship status, exclusivity, commitment, or relationship milestones.",
        new String[]{"boyfriend", "girlfriend", "partner", "relationship", "together", "exclusive", "commitment"}
    ),
    
    // Business/Professional Categories
    BUSINESS(
        "business",
        "Professional conversations about business opportunities, partnerships, or business-related topics. Includes discussing business ideas, partnerships, or professional relationships.",
        new String[]{"business", "company", "partnership", "deal", "opportunity", "enterprise", "corporate"}
    ),
    INVESTMENT(
        "investment",
        "Conversations about securing investment, pitching to investors, discussing funding rounds, or investor relationships. Includes startup funding, venture capital, or angel investment discussions.",
        new String[]{"investment", "investor", "funding", "pitch", "venture capital", "VC", "angel", "raise money"}
    ),
    SPONSORSHIP(
        "sponsorship",
        "Conversations about securing sponsorships, brand partnerships, or promotional deals. Includes discussing sponsorship opportunities, brand deals, or promotional arrangements.",
        new String[]{"sponsorship", "sponsor", "brand", "partnership", "promotional", "deal", "advertising"}
    ),
    NETWORKING(
        "networking",
        "Professional networking conversations, making business connections, or expanding professional network. Includes exchanging contacts, discussing industry topics, or making professional introductions.",
        new String[]{"network", "connection", "contact", "industry", "professional", "linkedin", "introduction"}
    ),
    COLLABORATION(
        "collaboration",
        "Conversations about working together on projects, partnerships, or joint ventures. Includes discussing collaboration opportunities, joint projects, or teaming up.",
        new String[]{"collaborate", "together", "project", "joint", "team", "partnership", "work with"}
    ),
    MENTORSHIP(
        "mentorship",
        "Conversations about mentoring relationships, seeking guidance, or providing advice. Includes discussions about career advice, guidance, or mentorship arrangements.",
        new String[]{"mentor", "mentorship", "guidance", "advice", "teach", "learn", "experience", "career"}
    ),
    SALES(
        "sales",
        "Conversations about selling products, services, or closing deals. Includes sales pitches, product demonstrations, or negotiating sales terms.",
        new String[]{"sell", "buy", "price", "deal", "product", "service", "offer", "purchase", "order"}
    ),
    
    // Academic/Educational Categories
    ACADEMIC(
        "academic",
        "Conversations related to academic studies, coursework, or educational topics. Includes discussing assignments, exams, research, or academic projects.",
        new String[]{"study", "homework", "assignment", "exam", "test", "research", "paper", "university", "college"}
    ),
    STUDIES(
        "studies",
        "Conversations about studying together, sharing study materials, or academic collaboration. Includes study groups, sharing notes, or studying together.",
        new String[]{"study together", "notes", "study group", "homework help", "exam preparation", "lecture"}
    ),
    EDUCATIONAL(
        "educational",
        "Conversations focused on teaching or learning. Includes educational discussions, explanations, or knowledge sharing.",
        new String[]{"teach", "learn", "explain", "understand", "knowledge", "education", "lesson", "tutorial"}
    ),
    
    // Social/Friendship Categories
    FRIENDSHIP(
        "friendship",
        "Conversations between friends, casual social interactions, or building friendships. Includes friendly banter, hanging out, or friend-related activities.",
        new String[]{"friend", "friendship", "hang out", "buddy", "pal", "mate", "social"}
    ),
    SOCIAL(
        "social",
        "General social conversations, small talk, or casual interactions. Includes general chatting, social events, or casual conversations.",
        new String[]{"social", "chat", "talk", "conversation", "casual", "small talk"}
    ),
    CASUAL(
        "casual",
        "Informal, relaxed conversations without specific goals. Includes everyday conversations, casual chatting, or informal interactions.",
        new String[]{"casual", "informal", "relaxed", "just chatting", "nothing special"}
    ),
    
    // Work/Professional Categories
    WORK(
        "work",
        "Work-related conversations, discussing job tasks, workplace matters, or professional work topics. Includes discussing projects, meetings, or workplace issues.",
        new String[]{"work", "job", "office", "project", "meeting", "task", "deadline", "workplace"}
    ),
    PROFESSIONAL(
        "professional",
        "Formal professional conversations maintaining professional boundaries. Includes formal communication, professional inquiries, or business correspondence.",
        new String[]{"professional", "formal", "sir", "madam", "regards", "sincerely", "businesslike"}
    ),
    JOB(
        "job",
        "Conversations about job opportunities, interviews, career changes, or employment-related topics. Includes discussing job openings, interviews, or career advice.",
        new String[]{"job", "interview", "hire", "career", "employment", "position", "role", "resume"}
    ),
    
    // Interest/Hobby Categories
    HOBBY(
        "hobby",
        "Conversations about shared hobbies, interests, or recreational activities. Includes discussing hobbies, interests, or activities enjoyed together.",
        new String[]{"hobby", "interest", "enjoy", "fun", "activity", "pastime", "leisure"}
    ),
    GAMING(
        "gaming",
        "Conversations about video games, gaming together, or discussing games. Includes game recommendations, gaming sessions, or game discussions.",
        new String[]{"game", "gaming", "play", "gamer", "console", "PC", "mobile game", "online game"}
    ),
    MUSIC(
        "music",
        "Conversations about music, sharing music, or discussing musical interests. Includes song recommendations, concerts, or music taste discussions.",
        new String[]{"music", "song", "album", "artist", "concert", "playlist", "spotify", "musical"}
    ),
    MOVIES(
        "movies",
        "Conversations about movies, TV shows, or entertainment content. Includes movie recommendations, discussing shows, or entertainment reviews.",
        new String[]{"movie", "film", "TV show", "series", "netflix", "cinema", "watch", "entertainment"}
    ),
    SPORTS(
        "sports",
        "Conversations about sports, watching games together, or discussing sports events. Includes game discussions, team preferences, or sports activities.",
        new String[]{"sports", "game", "team", "match", "player", "athletic", "fitness", "exercise"}
    ),
    TRAVEL(
        "travel",
        "Conversations about traveling, planning trips, or sharing travel experiences. Includes discussing destinations, travel plans, or travel stories.",
        new String[]{"travel", "trip", "vacation", "destination", "flight", "hotel", "explore", "journey"}
    ),
    FOOD(
        "food",
        "Conversations about food, restaurants, cooking, or dining together. Includes restaurant recommendations, cooking tips, or food preferences.",
        new String[]{"food", "restaurant", "cooking", "dining", "meal", "cuisine", "recipe", "taste"}
    ),
    FITNESS(
        "fitness",
        "Conversations about fitness, working out together, or health and wellness. Includes gym discussions, workout plans, or fitness goals.",
        new String[]{"fitness", "gym", "workout", "exercise", "health", "wellness", "training", "fit"}
    ),
    
    // Emotional/Support Categories
    SUPPORT(
        "support",
        "Conversations offering emotional support, encouragement, or help during difficult times. Includes comforting, encouraging, or supporting someone.",
        new String[]{"support", "help", "encourage", "comfort", "care", "there for you", "difficult time"}
    ),
    ADVICE(
        "advice",
        "Conversations seeking or giving advice on personal matters, problems, or life decisions. Includes asking for help, sharing problems, or offering guidance.",
        new String[]{"advice", "help", "problem", "issue", "guidance", "what should I do", "suggestion"}
    ),
    EMOTIONAL(
        "emotional",
        "Conversations involving deep emotions, sharing feelings, or emotional connections. Includes expressing emotions, deep conversations, or emotional bonding.",
        new String[]{"feel", "emotion", "feeling", "heart", "soul", "deep", "meaningful", "connection"}
    ),
    
    // Activity/Event Categories
    EVENT(
        "event",
        "Conversations about events, parties, gatherings, or social events. Includes planning events, inviting to events, or discussing upcoming events.",
        new String[]{"event", "party", "gathering", "celebration", "invitation", "attend", "venue"}
    ),
    PLANS(
        "plans",
        "Conversations about making plans together, scheduling activities, or coordinating meetups. Includes planning future activities or scheduling time together.",
        new String[]{"plan", "schedule", "when", "what time", "meet up", "arrange", "coordinate"}
    ),
    INVITATION(
        "invitation",
        "Conversations involving invitations to events, activities, or meetups. Includes inviting someone, accepting/declining invitations, or invitation-related discussions.",
        new String[]{"invite", "invitation", "come", "join", "welcome", "attend", "RSVP"}
    ),
    
    // Communication Style Categories
    FORMAL(
        "formal",
        "Formal conversations with professional or respectful tone. Includes formal language, proper greetings, and respectful communication.",
        new String[]{"sir", "madam", "respectfully", "formal", "professional", "proper"}
    ),
    INFORMAL(
        "informal",
        "Casual, relaxed conversations with informal language. Includes slang, casual expressions, or relaxed communication style.",
        new String[]{"hey", "yo", "sup", "casual", "relaxed", "chill", "informal"}
    ),
    HUMOROUS(
        "humorous",
        "Conversations with jokes, humor, or light-hearted banter. Includes jokes, funny comments, or humorous exchanges.",
        new String[]{"joke", "funny", "laugh", "haha", "humor", "hilarious", "comedy"}
    ),
    SERIOUS(
        "serious",
        "Serious, important conversations about significant topics. Includes important discussions, serious matters, or significant life topics.",
        new String[]{"serious", "important", "significant", "matter", "issue", "crucial", "critical"}
    ),
    
    // Problem/Conflict Categories
    PROBLEM(
        "problem",
        "Conversations about problems, issues, or conflicts that need resolution. Includes discussing problems, conflicts, or seeking solutions.",
        new String[]{"problem", "issue", "trouble", "conflict", "difficulty", "challenge", "resolve"}
    ),
    APOLOGY(
        "apology",
        "Conversations involving apologies, making amends, or resolving conflicts. Includes apologizing, forgiveness, or resolving misunderstandings.",
        new String[]{"sorry", "apology", "apologize", "forgive", "regret", "mistake", "wrong"}
    ),
    
    // Special Interest Categories
    TECHNOLOGY(
        "technology",
        "Conversations about technology, tech products, or tech-related topics. Includes discussing gadgets, software, or tech trends.",
        new String[]{"technology", "tech", "gadget", "software", "app", "device", "innovation", "digital"}
    ),
    ART(
        "art",
        "Conversations about art, creative projects, or artistic interests. Includes discussing artwork, creativity, or artistic endeavors.",
        new String[]{"art", "artistic", "creative", "painting", "drawing", "design", "creativity"}
    ),
    LITERATURE(
        "literature",
        "Conversations about books, reading, or literature. Includes book recommendations, discussing books, or literary topics.",
        new String[]{"book", "reading", "literature", "novel", "author", "story", "read"}
    ),
    
    // Financial Categories
    FINANCIAL(
        "financial",
        "Conversations about money, finances, or financial matters. Includes discussing expenses, budgeting, or financial planning.",
        new String[]{"money", "financial", "payment", "budget", "expense", "cost", "price", "pay"}
    ),
    
    // Health Categories
    HEALTH(
        "health",
        "Conversations about health, wellness, or medical topics. Includes discussing health issues, wellness, or medical concerns.",
        new String[]{"health", "wellness", "medical", "doctor", "sick", "illness", "treatment"}
    ),
    
    // Family/Family-Related Categories
    FAMILY(
        "family",
        "Conversations about family, family members, or family-related topics. Includes discussing family matters, family members, or family events.",
        new String[]{"family", "parent", "sibling", "relative", "mom", "dad", "brother", "sister"}
    ),
    
    // Location-Based Categories
    LOCAL(
        "local",
        "Conversations about local area, nearby places, or local activities. Includes discussing local spots, nearby places, or local events.",
        new String[]{"local", "nearby", "area", "neighborhood", "city", "place", "location"}
    ),
    
    // Time-Based Categories
    URGENT(
        "urgent",
        "Conversations requiring immediate attention or urgent matters. Includes time-sensitive discussions or urgent requests.",
        new String[]{"urgent", "immediate", "asap", "quickly", "now", "emergency", "important"}
    ),
    
    // Negotiation/Agreement Categories
    NEGOTIATION(
        "negotiation",
        "Conversations involving negotiations, making deals, or reaching agreements. Includes discussing terms, conditions, or reaching compromises.",
        new String[]{"negotiate", "deal", "terms", "agreement", "compromise", "settle", "arrange"}
    ),
    
    // Follow-up/Follow-through Categories
    FOLLOW_UP(
        "follow_up",
        "Conversations following up on previous discussions, checking in, or maintaining ongoing communication. Includes follow-up messages or checking progress.",
        new String[]{"follow up", "check", "update", "progress", "status", "how is", "update me"}
    ),
    
    // Apology/Reconciliation Categories
    RECONCILIATION(
        "reconciliation",
        "Conversations aimed at reconciling, mending relationships, or resolving conflicts. Includes making up, repairing relationships, or healing rifts.",
        new String[]{"reconcile", "make up", "mend", "fix", "repair", "heal", "move forward"}
    ),
    
    // Additional Romantic/Dating Categories
    BREAKUP(
        "breakup",
        "Conversations about ending relationships, breaking up, or relationship termination. Includes discussions about separation, moving on, or relationship endings.",
        new String[]{"breakup", "break up", "separate", "end relationship", "split", "part ways", "divorce"}
    ),
    FIRST_DATE(
        "first_date",
        "Conversations about first dates, initial romantic meetings, or first-time date planning. Includes nervous anticipation, first date suggestions, or first date experiences.",
        new String[]{"first date", "first time", "nervous", "butterflies", "first meeting", "first coffee"}
    ),
    LONG_DISTANCE(
        "long_distance",
        "Conversations about long-distance relationships, maintaining connection over distance, or managing separation. Includes video calls, visits, or distance challenges.",
        new String[]{"long distance", "LDR", "distance", "far away", "visit", "miss you", "separated"}
    ),
    ONLINE_DATING(
        "online_dating",
        "Conversations originating from online dating platforms, apps, or digital dating. Includes matching, profile discussions, or app-based dating.",
        new String[]{"tinder", "bumble", "hinge", "match", "online dating", "dating app", "profile"}
    ),
    BLIND_DATE(
        "blind_date",
        "Conversations about blind dates, set-ups by friends, or meeting someone arranged by others. Includes introductions, expectations, or set-up discussions.",
        new String[]{"blind date", "set up", "introduce", "matchmaker", "fixed up", "my friend wants"}
    ),
    SECOND_DATE(
        "second_date",
        "Conversations about follow-up dates, second meetings, or continuing romantic interest. Includes planning second dates or building on first date success.",
        new String[]{"second date", "next date", "again", "continue", "follow up", "another date"}
    ),
    COMMITMENT(
        "commitment",
        "Conversations about commitment levels, exclusivity, or relationship dedication. Includes discussing being exclusive, committed, or relationship status.",
        new String[]{"commitment", "exclusive", "committed", "serious", "dedicated", "relationship status"}
    ),
    MARRIAGE(
        "marriage",
        "Conversations about marriage, proposals, wedding planning, or marital relationships. Includes engagement, wedding discussions, or marital life.",
        new String[]{"marriage", "marry", "wedding", "proposal", "engaged", "spouse", "husband", "wife"}
    ),
    INTIMACY(
        "intimacy",
        "Conversations about physical intimacy, romantic connection, or intimate relationships. Includes discussions about physical closeness or romantic connection.",
        new String[]{"intimacy", "intimate", "physical", "close", "connected", "bond", "chemistry"}
    ),
    SEXUAL(
        "sexual",
        "Conversations with sexual content, discussions about sex, or sexual topics. Includes sexual topics, desires, or sexual communication.",
        new String[]{"sex", "sexual", "bed", "intimate", "desire", "attraction", "physical"}
    ),
    
    // Business Expansion Categories
    STARTUP(
        "startup",
        "Conversations about startups, founding companies, or entrepreneurial ventures. Includes discussing startup ideas, founding teams, or startup challenges.",
        new String[]{"startup", "founder", "entrepreneur", "venture", "new company", "start business"}
    ),
    PITCH(
        "pitch",
        "Conversations about pitching ideas, presenting proposals, or selling concepts. Includes pitch presentations, elevator pitches, or proposal discussions.",
        new String[]{"pitch", "present", "proposal", "idea", "concept", "elevator pitch", "presentation"}
    ),
    FUNDRAISING(
        "fundraising",
        "Conversations about raising funds, fundraising campaigns, or securing capital. Includes discussing fundraising strategies, campaigns, or capital raising.",
        new String[]{"fundraising", "raise funds", "capital", "money", "investment", "funding round"}
    ),
    PARTNERSHIP_DEAL(
        "partnership_deal",
        "Conversations about partnership deals, business partnerships, or strategic alliances. Includes negotiating partnerships, alliance terms, or partnership agreements.",
        new String[]{"partnership deal", "strategic partnership", "alliance", "joint venture", "partner"}
    ),
    CONTRACT(
        "contract",
        "Conversations about contracts, agreements, or legal business documents. Includes contract negotiations, terms discussions, or agreement finalization.",
        new String[]{"contract", "agreement", "terms", "legal", "document", "sign", "deal"}
    ),
    CLIENT(
        "client",
        "Conversations with clients, customer relationships, or client management. Includes client meetings, customer service, or client relations.",
        new String[]{"client", "customer", "account", "relationship", "service", "meeting"}
    ),
    SUPPLIER(
        "supplier",
        "Conversations with suppliers, vendors, or procurement discussions. Includes supplier relationships, vendor management, or procurement processes.",
        new String[]{"supplier", "vendor", "procurement", "purchase", "order", "supply chain"}
    ),
    EMPLOYEE(
        "employee",
        "Conversations about employees, hiring, or workforce management. Includes discussing employees, hiring processes, or workforce issues.",
        new String[]{"employee", "staff", "hire", "workforce", "team", "recruitment"}
    ),
    EMPLOYER(
        "employer",
        "Conversations with employers, about employment, or employer relationships. Includes employer communications, employment matters, or workplace relationships.",
        new String[]{"employer", "boss", "manager", "workplace", "job", "employment"}
    ),
    RECRUITMENT(
        "recruitment",
        "Conversations about recruiting, hiring processes, or talent acquisition. Includes discussing recruitment, hiring strategies, or talent search.",
        new String[]{"recruitment", "hiring", "talent", "recruit", "candidate", "interview"}
    ),
    INTERVIEW(
        "interview",
        "Conversations about interviews, interview preparation, or interview experiences. Includes job interviews, interview tips, or interview feedback.",
        new String[]{"interview", "interview prep", "job interview", "questions", "hiring interview"}
    ),
    PROMOTION(
        "promotion",
        "Conversations about promotions, career advancement, or moving up. Includes discussing promotions, career growth, or advancement opportunities.",
        new String[]{"promotion", "promoted", "advance", "career growth", "move up", "raise"}
    ),
    RESIGNATION(
        "resignation",
        "Conversations about resigning, leaving jobs, or job transitions. Includes resignation discussions, job changes, or career transitions.",
        new String[]{"resign", "quit", "leave", "job change", "transition", "new job"}
    ),
    TERMINATION(
        "termination",
        "Conversations about job termination, firing, or employment endings. Includes termination discussions, layoffs, or job loss.",
        new String[]{"terminated", "fired", "laid off", "let go", "job loss", "dismissed"}
    ),
    SALARY(
        "salary",
        "Conversations about salaries, compensation, or payment discussions. Includes salary negotiations, pay raises, or compensation packages.",
        new String[]{"salary", "pay", "wage", "compensation", "income", "earnings", "raise"}
    ),
    BENEFITS(
        "benefits",
        "Conversations about employee benefits, perks, or compensation benefits. Includes discussing benefits packages, perks, or employee benefits.",
        new String[]{"benefits", "perks", "insurance", "vacation", "paid time off", "benefits package"}
    ),
    WORK_FROM_HOME(
        "work_from_home",
        "Conversations about working from home, remote work, or telecommuting. Includes remote work discussions, WFH arrangements, or telecommuting.",
        new String[]{"work from home", "WFH", "remote work", "telecommute", "home office", "remote"}
    ),
    OFFICE(
        "office",
        "Conversations about office matters, workplace issues, or office environments. Includes office politics, workplace culture, or office discussions.",
        new String[]{"office", "workplace", "coworker", "colleague", "office politics", "workplace"}
    ),
    MEETING(
        "meeting",
        "Conversations about meetings, scheduling meetings, or meeting discussions. Includes meeting planning, meeting notes, or meeting follow-ups.",
        new String[]{"meeting", "schedule", "appointment", "conference", "discuss", "meet up"}
    ),
    CONFERENCE(
        "conference",
        "Conversations about conferences, conferences attendance, or conference networking. Includes conference planning, conference discussions, or conference networking.",
        new String[]{"conference", "convention", "summit", "event", "networking event", "attend"}
    ),
    SEMINAR(
        "seminar",
        "Conversations about seminars, training sessions, or educational workshops. Includes seminar attendance, training discussions, or workshop participation.",
        new String[]{"seminar", "workshop", "training", "session", "educational", "learning"}
    ),
    PRESENTATION(
        "presentation",
        "Conversations about presentations, giving presentations, or presentation preparation. Includes presentation planning, presentation skills, or presentation feedback.",
        new String[]{"presentation", "present", "speak", "public speaking", "talk", "demo"}
    ),
    PROPOSAL(
        "proposal",
        "Conversations about proposals, submitting proposals, or proposal discussions. Includes proposal writing, proposal review, or proposal acceptance.",
        new String[]{"proposal", "submit", "suggest", "offer", "suggestion", "propose"}
    ),
    DEADLINE(
        "deadline",
        "Conversations about deadlines, time-sensitive tasks, or urgent deadlines. Includes deadline discussions, deadline pressure, or deadline management.",
        new String[]{"deadline", "due date", "urgent", "time sensitive", "soon", "by when"}
    ),
    PROJECT(
        "project",
        "Conversations about projects, project management, or project collaboration. Includes project planning, project updates, or project discussions.",
        new String[]{"project", "task", "assignment", "work on", "collaborate", "project management"}
    ),
    TEAM(
        "team",
        "Conversations about teams, teamwork, or team collaboration. Includes team discussions, team building, or team coordination.",
        new String[]{"team", "teamwork", "collaborate", "together", "group", "team member"}
    ),
    LEADERSHIP(
        "leadership",
        "Conversations about leadership, leading teams, or leadership roles. Includes leadership discussions, management, or leadership skills.",
        new String[]{"leadership", "leader", "manage", "direct", "lead", "management", "boss"}
    ),
    MANAGEMENT(
        "management",
        "Conversations about management, managing teams, or management roles. Includes management discussions, supervisory roles, or management responsibilities.",
        new String[]{"management", "manager", "supervisor", "oversee", "manage", "direct"}
    ),
    CONSULTING(
        "consulting",
        "Conversations about consulting, consulting services, or consulting projects. Includes consulting discussions, consultant relationships, or consulting advice.",
        new String[]{"consulting", "consultant", "advice", "expert", "consult", "professional advice"}
    ),
    FREELANCE(
        "freelance",
        "Conversations about freelance work, freelance projects, or freelance arrangements. Includes freelance opportunities, freelance discussions, or freelance contracts.",
        new String[]{"freelance", "freelancer", "contractor", "independent", "gig", "project based"}
    ),
    ENTREPRENEURSHIP(
        "entrepreneurship",
        "Conversations about entrepreneurship, starting businesses, or entrepreneurial ventures. Includes entrepreneurial discussions, business ideas, or startup ventures.",
        new String[]{"entrepreneurship", "entrepreneur", "business owner", "startup", "venture", "business"}
    ),
    INNOVATION(
        "innovation",
        "Conversations about innovation, innovative ideas, or creative solutions. Includes innovation discussions, creative thinking, or innovative approaches.",
        new String[]{"innovation", "innovative", "creative", "new idea", "breakthrough", "invention"}
    ),
    MARKETING(
        "marketing",
        "Conversations about marketing, marketing strategies, or marketing campaigns. Includes marketing discussions, advertising, or marketing planning.",
        new String[]{"marketing", "advertise", "promote", "campaign", "brand", "marketing strategy"}
    ),
    ADVERTISING(
        "advertising",
        "Conversations about advertising, ad campaigns, or advertising strategies. Includes ad discussions, advertising planning, or advertising campaigns.",
        new String[]{"advertising", "ads", "ad campaign", "commercial", "promote", "marketing"}
    ),
    BRAND(
        "brand",
        "Conversations about brands, branding, or brand identity. Includes brand discussions, brand strategy, or brand management.",
        new String[]{"brand", "branding", "brand identity", "brand strategy", "branding"}
    ),
    PUBLIC_RELATIONS(
        "public_relations",
        "Conversations about public relations, PR strategies, or public image. Includes PR discussions, media relations, or reputation management.",
        new String[]{"public relations", "PR", "media relations", "reputation", "public image", "press"}
    ),
    CUSTOMER_SERVICE(
        "customer_service",
        "Conversations about customer service, client support, or customer care. Includes customer service discussions, support services, or customer assistance.",
        new String[]{"customer service", "support", "help desk", "client support", "service", "assist"}
    ),
    TECH_SUPPORT(
        "tech_support",
        "Conversations about technical support, IT help, or technology assistance. Includes tech support discussions, IT assistance, or technical help.",
        new String[]{"tech support", "IT support", "technical help", "computer help", "IT assistance"}
    ),
    QUALITY_ASSURANCE(
        "quality_assurance",
        "Conversations about quality assurance, QA processes, or quality control. Includes QA discussions, quality control, or quality management.",
        new String[]{"quality assurance", "QA", "quality control", "testing", "quality", "standards"}
    ),
    COMPLIANCE(
        "compliance",
        "Conversations about compliance, regulatory requirements, or legal compliance. Includes compliance discussions, regulations, or compliance requirements.",
        new String[]{"compliance", "regulatory", "regulations", "legal", "requirements", "standards"}
    ),
    RISK_MANAGEMENT(
        "risk_management",
        "Conversations about risk management, risk assessment, or risk mitigation. Includes risk discussions, risk analysis, or risk strategies.",
        new String[]{"risk management", "risk", "risk assessment", "mitigation", "risk analysis"}
    ),
    STRATEGY(
        "strategy",
        "Conversations about strategies, strategic planning, or strategic decisions. Includes strategy discussions, planning, or strategic thinking.",
        new String[]{"strategy", "strategic", "plan", "planning", "approach", "strategic planning"}
    ),
    OPERATIONS(
        "operations",
        "Conversations about operations, operational processes, or business operations. Includes operational discussions, process management, or operations.",
        new String[]{"operations", "operational", "process", "operations management", "business ops"}
    ),
    LOGISTICS(
        "logistics",
        "Conversations about logistics, supply chain, or distribution. Includes logistics discussions, supply chain management, or distribution planning.",
        new String[]{"logistics", "supply chain", "distribution", "shipping", "delivery", "transport"}
    ),
    REAL_ESTATE(
        "real_estate",
        "Conversations about real estate, property, or real estate transactions. Includes property discussions, real estate deals, or real estate investments.",
        new String[]{"real estate", "property", "real estate", "property investment", "housing", "property"}
    ),
    INSURANCE(
        "insurance",
        "Conversations about insurance, insurance policies, or insurance claims. Includes insurance discussions, policy reviews, or insurance coverage.",
        new String[]{"insurance", "policy", "coverage", "claim", "insured", "insurance policy"}
    ),
    LEGAL(
        "legal",
        "Conversations about legal matters, legal advice, or legal issues. Includes legal discussions, legal counsel, or legal matters.",
        new String[]{"legal", "law", "lawyer", "attorney", "legal advice", "legal matter"}
    ),
    LAWSUIT(
        "lawsuit",
        "Conversations about lawsuits, legal disputes, or litigation. Includes lawsuit discussions, legal disputes, or court cases.",
        new String[]{"lawsuit", "litigation", "sue", "court", "legal dispute", "lawsuit"}
    ),
    CONTRACT_NEGOTIATION(
        "contract_negotiation",
        "Conversations about contract negotiations, negotiating terms, or contract discussions. Includes contract talks, term negotiations, or agreement discussions.",
        new String[]{"contract negotiation", "negotiate", "terms", "contract terms", "agreement"}
    ),
    MERGER(
        "merger",
        "Conversations about mergers, acquisitions, or business combinations. Includes merger discussions, acquisition talks, or business combinations.",
        new String[]{"merger", "acquisition", "M&A", "merge", "acquire", "business combination"}
    ),
    BANKING(
        "banking",
        "Conversations about banking, bank services, or financial banking. Includes banking discussions, bank accounts, or banking services.",
        new String[]{"banking", "bank", "account", "banking service", "financial institution", "bank"}
    ),
    CREDIT(
        "credit",
        "Conversations about credit, credit scores, or credit management. Includes credit discussions, credit scores, or credit applications.",
        new String[]{"credit", "credit score", "credit card", "loan", "credit application", "credit"}
    ),
    LOAN(
        "loan",
        "Conversations about loans, borrowing money, or loan applications. Includes loan discussions, loan applications, or borrowing money.",
        new String[]{"loan", "borrow", "lend", "loan application", "borrow money", "credit"}
    ),
    TAX(
        "tax",
        "Conversations about taxes, tax preparation, or tax matters. Includes tax discussions, tax preparation, or tax filing.",
        new String[]{"tax", "taxes", "tax return", "IRS", "tax filing", "tax preparation"}
    ),
    ACCOUNTING(
        "accounting",
        "Conversations about accounting, financial accounting, or bookkeeping. Includes accounting discussions, financial records, or accounting services.",
        new String[]{"accounting", "accountant", "bookkeeping", "financial records", "accounting"}
    ),
    BUDGETING(
        "budgeting",
        "Conversations about budgeting, budget planning, or financial budgets. Includes budget discussions, budget planning, or budget management.",
        new String[]{"budget", "budgeting", "budget plan", "financial plan", "spending", "budget"}
    ),
    RETIREMENT(
        "retirement",
        "Conversations about retirement, retirement planning, or retirement savings. Includes retirement discussions, retirement planning, or retirement accounts.",
        new String[]{"retirement", "retire", "pension", "401k", "retirement plan", "retirement savings"}
    ),
    STOCK(
        "stock",
        "Conversations about stocks, stock trading, or stock investments. Includes stock discussions, stock market, or stock trading.",
        new String[]{"stock", "stocks", "stock market", "trading", "invest", "shares"}
    ),
    CRYPTOCURRENCY(
        "cryptocurrency",
        "Conversations about cryptocurrency, crypto trading, or blockchain. Includes crypto discussions, Bitcoin, or cryptocurrency investments.",
        new String[]{"cryptocurrency", "crypto", "bitcoin", "blockchain", "crypto trading", "crypto"}
    ),
    TRADING(
        "trading",
        "Conversations about trading, trading strategies, or investment trading. Includes trading discussions, trading strategies, or trading activities.",
        new String[]{"trading", "trade", "trader", "trading strategy", "invest", "market"}
    ),
    FOREX(
        "forex",
        "Conversations about forex, foreign exchange, or currency trading. Includes forex discussions, currency trading, or foreign exchange.",
        new String[]{"forex", "foreign exchange", "currency trading", "FX", "currency", "forex trading"}
    ),
    
    // Academic/Educational Expansion
    HOMEWORK(
        "homework",
        "Conversations about homework, assignments, or academic tasks. Includes homework help, assignment discussions, or academic work.",
        new String[]{"homework", "assignment", "due", "submit", "academic work", "school work"}
    ),
    EXAM(
        "exam",
        "Conversations about exams, tests, or assessments. Includes exam preparation, exam results, or test discussions.",
        new String[]{"exam", "test", "quiz", "assessment", "exam prep", "exam results"}
    ),
    RESEARCH(
        "research",
        "Conversations about research, research projects, or academic research. Includes research discussions, research methods, or research findings.",
        new String[]{"research", "study", "investigation", "research project", "academic research", "findings"}
    ),
    THESIS(
        "thesis",
        "Conversations about thesis, dissertation, or major research papers. Includes thesis writing, dissertation work, or thesis defense.",
        new String[]{"thesis", "dissertation", "major paper", "thesis defense", "research paper"}
    ),
    CLASS(
        "class",
        "Conversations about classes, lectures, or course discussions. Includes class notes, lecture discussions, or course topics.",
        new String[]{"class", "lecture", "course", "lesson", "classroom", "course material"}
    ),
    GRADES(
        "grades",
        "Conversations about grades, academic performance, or grading. Includes grade discussions, academic performance, or grading policies.",
        new String[]{"grade", "grades", "GPA", "score", "academic performance", "grading"}
    ),
    STUDY_GROUP(
        "study_group",
        "Conversations about study groups, group study sessions, or collaborative studying. Includes study group planning, group study, or collaborative learning.",
        new String[]{"study group", "group study", "study together", "collaborative study", "study session"}
    ),
    TUTORING(
        "tutoring",
        "Conversations about tutoring, tutoring sessions, or academic tutoring. Includes tutor relationships, tutoring sessions, or academic help.",
        new String[]{"tutor", "tutoring", "academic help", "tutoring session", "teach", "learn"}
    ),
    SCHOLARSHIP(
        "scholarship",
        "Conversations about scholarships, financial aid, or educational funding. Includes scholarship applications, financial aid, or education funding.",
        new String[]{"scholarship", "financial aid", "education funding", "grant", "scholarship application"}
    ),
    COLLEGE_ADMISSION(
        "college_admission",
        "Conversations about college admissions, university applications, or educational enrollment. Includes admission discussions, application processes, or enrollment.",
        new String[]{"college admission", "university application", "admission", "enrollment", "apply to college"}
    ),
    GRADUATION(
        "graduation",
        "Conversations about graduation, graduating, or completion of education. Includes graduation ceremonies, graduation planning, or educational completion.",
        new String[]{"graduation", "graduate", "diploma", "commencement", "finish school", "graduate"}
    ),
    CERTIFICATION(
        "certification",
        "Conversations about certifications, professional certifications, or credentialing. Includes certification programs, professional credentials, or certification exams.",
        new String[]{"certification", "certificate", "credential", "certified", "professional certification"}
    ),
    TRAINING(
        "training",
        "Conversations about training, training programs, or skill development. Includes training sessions, skill development, or professional training.",
        new String[]{"training", "train", "skill development", "training program", "learn new skills"}
    ),
    WORKSHOP(
        "workshop",
        "Conversations about workshops, hands-on learning, or workshop participation. Includes workshop attendance, hands-on learning, or workshop discussions.",
        new String[]{"workshop", "hands-on", "practical learning", "workshop attendance", "interactive"}
    ),
    ONLINE_COURSE(
        "online_course",
        "Conversations about online courses, e-learning, or distance education. Includes online learning, e-learning platforms, or distance education.",
        new String[]{"online course", "e-learning", "distance education", "online learning", "MOOC", "online class"}
    ),
    LANGUAGE_LEARNING(
        "language_learning",
        "Conversations about learning languages, language practice, or language exchange. Includes language learning, language practice, or language exchange.",
        new String[]{"language learning", "learn language", "language practice", "language exchange", "fluent"}
    ),
    BOOK_CLUB(
        "book_club",
        "Conversations about book clubs, reading groups, or book discussions. Includes book club meetings, book discussions, or reading groups.",
        new String[]{"book club", "reading group", "book discussion", "literature club", "reading"}
    ),
    
    // Technology Categories
    PROGRAMMING(
        "programming",
        "Conversations about programming, coding, or software development. Includes coding discussions, programming languages, or software development.",
        new String[]{"programming", "coding", "code", "developer", "software development", "programming language"}
    ),
    SOFTWARE(
        "software",
        "Conversations about software, software applications, or software usage. Includes software discussions, applications, or software tools.",
        new String[]{"software", "app", "application", "program", "software tool", "software"}
    ),
    HARDWARE(
        "hardware",
        "Conversations about hardware, computer hardware, or electronic devices. Includes hardware discussions, computer components, or electronic devices.",
        new String[]{"hardware", "computer hardware", "device", "component", "equipment", "electronic"}
    ),
    GADGET(
        "gadget",
        "Conversations about gadgets, electronic gadgets, or tech gadgets. Includes gadget discussions, new gadgets, or tech devices.",
        new String[]{"gadget", "device", "tech gadget", "electronic gadget", "new gadget"}
    ),
    SMARTPHONE(
        "smartphone",
        "Conversations about smartphones, mobile phones, or phone discussions. Includes phone discussions, mobile phones, or smartphone features.",
        new String[]{"smartphone", "phone", "mobile phone", "iPhone", "Android", "cell phone"}
    ),
    LAPTOP(
        "laptop",
        "Conversations about laptops, computers, or laptop discussions. Includes laptop recommendations, computer discussions, or laptop features.",
        new String[]{"laptop", "computer", "notebook", "PC", "Mac", "computer"}
    ),
    INTERNET(
        "internet",
        "Conversations about internet, web, or online connectivity. Includes internet discussions, web browsing, or online connectivity.",
        new String[]{"internet", "web", "online", "connectivity", "browsing", "WWW"}
    ),
    WEBSITE(
        "website",
        "Conversations about websites, web development, or website design. Includes website discussions, web development, or website creation.",
        new String[]{"website", "web site", "web development", "web design", "site", "web"}
    ),
    APP_DEVELOPMENT(
        "app_development",
        "Conversations about app development, mobile apps, or application development. Includes app development, mobile app creation, or application programming.",
        new String[]{"app development", "mobile app", "application development", "develop app", "app creation"}
    ),
    GAME_DEVELOPMENT(
        "game_development",
        "Conversations about game development, creating games, or game programming. Includes game development, game design, or game programming.",
        new String[]{"game development", "game design", "game programming", "create game", "game dev"}
    ),
    ARTIFICIAL_INTELLIGENCE(
        "artificial_intelligence",
        "Conversations about artificial intelligence, AI, or machine learning. Includes AI discussions, machine learning, or artificial intelligence topics.",
        new String[]{"artificial intelligence", "AI", "machine learning", "ML", "neural network", "AI"}
    ),
    DATA_SCIENCE(
        "data_science",
        "Conversations about data science, data analysis, or data processing. Includes data science discussions, data analysis, or big data.",
        new String[]{"data science", "data analysis", "big data", "data processing", "analytics", "data"}
    ),
    CYBERSECURITY(
        "cybersecurity",
        "Conversations about cybersecurity, online security, or digital security. Includes security discussions, cybersecurity, or online safety.",
        new String[]{"cybersecurity", "security", "online security", "digital security", "hacking", "security"}
    ),
    HACKING(
        "hacking",
        "Conversations about hacking, ethical hacking, or security vulnerabilities. Includes hacking discussions, security testing, or ethical hacking.",
        new String[]{"hacking", "hacker", "ethical hacking", "security testing", "vulnerability", "hack"}
    ),
    CLOUD_COMPUTING(
        "cloud_computing",
        "Conversations about cloud computing, cloud services, or cloud storage. Includes cloud discussions, cloud services, or cloud platforms.",
        new String[]{"cloud computing", "cloud", "cloud storage", "AWS", "Azure", "cloud service"}
    ),
    DATABASE(
        "database",
        "Conversations about databases, data storage, or database management. Includes database discussions, SQL, or data management.",
        new String[]{"database", "SQL", "data storage", "database management", "DB", "data"}
    ),
    NETWORKING_TECH(
        "networking_tech",
        "Conversations about computer networking, network administration, or network infrastructure. Includes networking discussions, network setup, or network administration.",
        new String[]{"networking", "network", "network administration", "network setup", "infrastructure"}
    ),
    IT_ADMINISTRATION(
        "it_administration",
        "Conversations about IT administration, system administration, or IT management. Includes IT discussions, system admin, or IT management.",
        new String[]{"IT administration", "system admin", "IT management", "system administration", "IT"}
    ),
    TECH_REVIEW(
        "tech_review",
        "Conversations about technology reviews, product reviews, or tech product evaluations. Includes tech reviews, product evaluations, or technology comparisons.",
        new String[]{"tech review", "product review", "review", "evaluation", "compare", "technology review"}
    ),
    TROUBLESHOOTING(
        "troubleshooting",
        "Conversations about troubleshooting, fixing problems, or technical problem-solving. Includes troubleshooting discussions, fixing issues, or problem-solving.",
        new String[]{"troubleshooting", "fix", "problem", "issue", "solve", "technical problem"}
    ),
    UPDATES(
        "updates",
        "Conversations about updates, software updates, or system updates. Includes update discussions, software updates, or system maintenance.",
        new String[]{"update", "updates", "software update", "system update", "upgrade", "patch"}
    ),
    
    // Entertainment Categories
    MOVIE_REVIEW(
        "movie_review",
        "Conversations about movie reviews, film critiques, or movie opinions. Includes movie reviews, film discussions, or movie critiques.",
        new String[]{"movie review", "film review", "review", "critique", "movie opinion", "film critique"}
    ),
    TV_SHOW(
        "tv_show",
        "Conversations about TV shows, television series, or television programs. Includes TV show discussions, series watching, or television content.",
        new String[]{"TV show", "television", "series", "episode", "season", "TV"}
    ),
    NETFLIX(
        "netflix",
        "Conversations about Netflix, Netflix shows, or Netflix recommendations. Includes Netflix discussions, show recommendations, or streaming content.",
        new String[]{"Netflix", "streaming", "binge watch", "Netflix show", "stream", "watch"}
    ),
    CONCERT(
        "concert",
        "Conversations about concerts, live music events, or musical performances. Includes concert discussions, live music, or concert attendance.",
        new String[]{"concert", "live music", "performance", "gig", "show", "musical performance"}
    ),
    FESTIVAL(
        "festival",
        "Conversations about festivals, music festivals, or cultural festivals. Includes festival discussions, festival attendance, or festival planning.",
        new String[]{"festival", "music festival", "event", "festival attendance", "cultural festival"}
    ),
    THEATER(
        "theater",
        "Conversations about theater, plays, or theatrical performances. Includes theater discussions, play attendance, or theatrical content.",
        new String[]{"theater", "theatre", "play", "drama", "theatrical", "stage", "performance"}
    ),
    COMEDY(
        "comedy",
        "Conversations about comedy, stand-up comedy, or humorous entertainment. Includes comedy discussions, stand-up shows, or humorous content.",
        new String[]{"comedy", "stand-up", "funny", "humor", "comedian", "joke", "comedy show"}
    ),
    PODCAST(
        "podcast",
        "Conversations about podcasts, podcast recommendations, or podcast listening. Includes podcast discussions, podcast recommendations, or audio content.",
        new String[]{"podcast", "podcast recommendation", "listen", "audio", "podcast episode"}
    ),
    YOUTUBE(
        "youtube",
        "Conversations about YouTube, YouTube videos, or YouTube content. Includes YouTube discussions, video sharing, or YouTube channels.",
        new String[]{"YouTube", "video", "vlog", "YouTube channel", "watch", "YouTube video"}
    ),
    STREAMING(
        "streaming",
        "Conversations about streaming, streaming services, or streaming content. Includes streaming discussions, streaming platforms, or streaming services.",
        new String[]{"streaming", "stream", "streaming service", "streaming platform", "watch online"}
    ),
    BOOK_RECOMMENDATION(
        "book_recommendation",
        "Conversations about book recommendations, reading suggestions, or book discussions. Includes book recommendations, reading lists, or book suggestions.",
        new String[]{"book recommendation", "reading suggestion", "book suggestion", "read", "book list"}
    ),
    MAGAZINE(
        "magazine",
        "Conversations about magazines, magazine articles, or magazine reading. Includes magazine discussions, article reading, or magazine subscriptions.",
        new String[]{"magazine", "article", "magazine article", "periodical", "magazine subscription"}
    ),
    NEWS(
        "news",
        "Conversations about news, current events, or news articles. Includes news discussions, current events, or news reading.",
        new String[]{"news", "current events", "news article", "headlines", "breaking news", "news"}
    ),
    GOSSIP(
        "gossip",
        "Conversations about gossip, rumors, or celebrity gossip. Includes gossip discussions, rumors, or celebrity news.",
        new String[]{"gossip", "rumor", "rumour", "celebrity gossip", "hearsay", "gossip"}
    ),
    CELEBRITY(
        "celebrity",
        "Conversations about celebrities, celebrity news, or celebrity discussions. Includes celebrity discussions, celebrity news, or celebrity gossip.",
        new String[]{"celebrity", "star", "famous", "celebrity news", "Hollywood", "celebrity"}
    ),
    
    // Gaming Categories
    VIDEO_GAME(
        "video_game",
        "Conversations about video games, gaming, or game discussions. Includes game discussions, gaming sessions, or video game reviews.",
        new String[]{"video game", "game", "gaming", "play", "gamer", "video game review"}
    ),
    MOBILE_GAME(
        "mobile_game",
        "Conversations about mobile games, phone games, or mobile gaming. Includes mobile game discussions, phone games, or mobile gaming.",
        new String[]{"mobile game", "phone game", "app game", "mobile gaming", "iOS game", "Android game"}
    ),
    PC_GAME(
        "pc_game",
        "Conversations about PC games, computer games, or PC gaming. Includes PC game discussions, computer games, or PC gaming.",
        new String[]{"PC game", "computer game", "PC gaming", "desktop game", "PC"}
    ),
    CONSOLE_GAME(
        "console_game",
        "Conversations about console games, gaming consoles, or console gaming. Includes console game discussions, PlayStation, Xbox, or Nintendo.",
        new String[]{"console game", "PlayStation", "Xbox", "Nintendo", "console", "gaming console"}
    ),
    ONLINE_GAME(
        "online_game",
        "Conversations about online games, multiplayer games, or online gaming. Includes online game discussions, multiplayer gaming, or online play.",
        new String[]{"online game", "multiplayer", "online gaming", "MMO", "online play", "multiplayer game"}
    ),
    GAMING_STRATEGY(
        "gaming_strategy",
        "Conversations about gaming strategies, game tips, or gaming tactics. Includes strategy discussions, game tips, or gaming tactics.",
        new String[]{"gaming strategy", "game tip", "strategy", "tactics", "game guide", "how to play"}
    ),
    ESPORTS(
        "esports",
        "Conversations about esports, competitive gaming, or gaming tournaments. Includes esports discussions, competitive gaming, or gaming tournaments.",
        new String[]{"esports", "competitive gaming", "gaming tournament", "pro gamer", "esports team"}
    ),
    GAME_UPDATE(
        "game_update",
        "Conversations about game updates, game patches, or game improvements. Includes update discussions, game patches, or game improvements.",
        new String[]{"game update", "game patch", "update", "patch", "game improvement", "new update"}
    ),
    GAMING_HARDWARE(
        "gaming_hardware",
        "Conversations about gaming hardware, gaming equipment, or gaming peripherals. Includes gaming hardware discussions, gaming equipment, or gaming gear.",
        new String[]{"gaming hardware", "gaming equipment", "gaming gear", "gaming peripherals", "gaming setup"}
    ),
    
    // Music Categories
    MUSIC_GENRE(
        "music_genre",
        "Conversations about music genres, music styles, or musical preferences. Includes genre discussions, music styles, or musical taste.",
        new String[]{"music genre", "genre", "music style", "musical taste", "music preference"}
    ),
    PLAYLIST(
        "playlist",
        "Conversations about playlists, music playlists, or playlist sharing. Includes playlist discussions, playlist creation, or playlist sharing.",
        new String[]{"playlist", "music playlist", "playlist creation", "song list", "music collection"}
    ),
    MUSIC_PRODUCTION(
        "music_production",
        "Conversations about music production, making music, or music creation. Includes music production, song creation, or music making.",
        new String[]{"music production", "make music", "music creation", "produce music", "song creation"}
    ),
    INSTRUMENT(
        "instrument",
        "Conversations about musical instruments, playing instruments, or instrument discussions. Includes instrument discussions, playing instruments, or instrument learning.",
        new String[]{"instrument", "musical instrument", "play instrument", "guitar", "piano", "instrument"}
    ),
    SINGING(
        "singing",
        "Conversations about singing, vocals, or vocal performance. Includes singing discussions, vocal performance, or singing lessons.",
        new String[]{"singing", "sing", "vocals", "vocal performance", "voice", "singing lesson"}
    ),
    BAND(
        "band",
        "Conversations about bands, musical groups, or band discussions. Includes band discussions, musical groups, or band activities.",
        new String[]{"band", "musical group", "group", "band member", "band practice", "band"}
    ),
    RECORDING(
        "recording",
        "Conversations about recording music, studio recording, or audio recording. Includes recording discussions, studio work, or audio recording.",
        new String[]{"recording", "studio", "record music", "audio recording", "studio recording"}
    ),
    SONGWRITING(
        "songwriting",
        "Conversations about songwriting, writing songs, or song creation. Includes songwriting discussions, lyric writing, or song creation.",
        new String[]{"songwriting", "write song", "lyrics", "song creation", "write music", "compose"}
    ),
    
    // Sports Categories
    FOOTBALL(
        "football",
        "Conversations about football, soccer, or football games. Includes football discussions, soccer, or football matches.",
        new String[]{"football", "soccer", "match", "game", "team", "football match"}
    ),
    BASKETBALL(
        "basketball",
        "Conversations about basketball, basketball games, or basketball teams. Includes basketball discussions, NBA, or basketball matches.",
        new String[]{"basketball", "NBA", "game", "team", "basketball game", "basketball match"}
    ),
    BASEBALL(
        "baseball",
        "Conversations about baseball, baseball games, or baseball teams. Includes baseball discussions, MLB, or baseball matches.",
        new String[]{"baseball", "MLB", "game", "team", "baseball game", "baseball match"}
    ),
    TENNIS(
        "tennis",
        "Conversations about tennis, tennis matches, or tennis tournaments. Includes tennis discussions, tennis matches, or tennis tournaments.",
        new String[]{"tennis", "match", "tournament", "tennis match", "tennis game", "tennis"}
    ),
    GOLF(
        "golf",
        "Conversations about golf, golf games, or golf courses. Includes golf discussions, golf matches, or golf courses.",
        new String[]{"golf", "golf course", "golf game", "golf match", "golfing", "golf"}
    ),
    SOCCER(
        "soccer",
        "Conversations about soccer, football, or soccer matches. Includes soccer discussions, football matches, or soccer games.",
        new String[]{"soccer", "football", "match", "game", "team", "soccer match"}
    ),
    RUNNING(
        "running",
        "Conversations about running, jogging, or running activities. Includes running discussions, jogging, or running training.",
        new String[]{"running", "jogging", "run", "marathon", "training", "running"}
    ),
    CYCLING(
        "cycling",
        "Conversations about cycling, biking, or bicycle activities. Includes cycling discussions, biking, or bicycle rides.",
        new String[]{"cycling", "biking", "bicycle", "bike", "ride", "cycling"}
    ),
    SWIMMING(
        "swimming",
        "Conversations about swimming, swimming pools, or swimming activities. Includes swimming discussions, swimming pools, or swimming training.",
        new String[]{"swimming", "pool", "swim", "swimming pool", "swimming training", "swimming"}
    ),
    WORKOUT(
        "workout",
        "Conversations about workouts, exercise routines, or fitness workouts. Includes workout discussions, exercise routines, or fitness training.",
        new String[]{"workout", "exercise", "training", "fitness", "exercise routine", "workout"}
    ),
    GYM(
        "gym",
        "Conversations about gym, gym workouts, or gym memberships. Includes gym discussions, gym workouts, or gym memberships.",
        new String[]{"gym", "workout", "gym membership", "fitness center", "gym workout", "gym"}
    ),
    YOGA(
        "yoga",
        "Conversations about yoga, yoga classes, or yoga practice. Includes yoga discussions, yoga classes, or yoga practice.",
        new String[]{"yoga", "yoga class", "yoga practice", "meditation", "yoga session", "yoga"}
    ),
    MARTIAL_ARTS(
        "martial_arts",
        "Conversations about martial arts, fighting sports, or martial arts training. Includes martial arts discussions, training, or martial arts classes.",
        new String[]{"martial arts", "karate", "judo", "taekwondo", "martial arts training", "fighting"}
    ),
    OLYMPICS(
        "olympics",
        "Conversations about Olympics, Olympic games, or Olympic sports. Includes Olympic discussions, Olympic games, or Olympic events.",
        new String[]{"Olympics", "Olympic games", "Olympic", "Olympic event", "Olympic sport"}
    ),
    FANTASY_SPORTS(
        "fantasy_sports",
        "Conversations about fantasy sports, fantasy leagues, or fantasy football. Includes fantasy sports discussions, fantasy leagues, or fantasy drafts.",
        new String[]{"fantasy sports", "fantasy league", "fantasy football", "fantasy", "fantasy draft"}
    ),
    
    // Food Categories
    RECIPE(
        "recipe",
        "Conversations about recipes, cooking recipes, or recipe sharing. Includes recipe discussions, cooking recipes, or recipe recommendations.",
        new String[]{"recipe", "cooking recipe", "recipe sharing", "cook", "recipe recommendation"}
    ),
    COOKING(
        "cooking",
        "Conversations about cooking, cooking techniques, or cooking activities. Includes cooking discussions, cooking techniques, or cooking classes.",
        new String[]{"cooking", "cook", "cooking technique", "culinary", "cooking class", "cooking"}
    ),
    BAKING(
        "baking",
        "Conversations about baking, baked goods, or baking recipes. Includes baking discussions, baking recipes, or baking activities.",
        new String[]{"baking", "bake", "baked goods", "pastry", "baking recipe", "baking"}
    ),
    RESTAURANT(
        "restaurant",
        "Conversations about restaurants, dining out, or restaurant recommendations. Includes restaurant discussions, dining out, or restaurant reviews.",
        new String[]{"restaurant", "dining out", "dine", "restaurant recommendation", "restaurant review"}
    ),
    CUISINE(
        "cuisine",
        "Conversations about cuisines, food cultures, or culinary traditions. Includes cuisine discussions, food cultures, or culinary traditions.",
        new String[]{"cuisine", "food culture", "culinary tradition", "food style", "cuisine type"}
    ),
    FOOD_DELIVERY(
        "food_delivery",
        "Conversations about food delivery, takeout, or food ordering. Includes food delivery discussions, takeout orders, or food ordering.",
        new String[]{"food delivery", "takeout", "delivery", "order food", "food ordering", "delivery"}
    ),
    DIET(
        "diet",
        "Conversations about diets, dieting, or dietary restrictions. Includes diet discussions, dieting, or dietary plans.",
        new String[]{"diet", "dieting", "dietary", "diet plan", "lose weight", "diet"}
    ),
    VEGETARIAN(
        "vegetarian",
        "Conversations about vegetarianism, vegetarian food, or plant-based diets. Includes vegetarian discussions, plant-based diets, or vegetarian food.",
        new String[]{"vegetarian", "veggie", "plant-based", "vegetarian food", "meat-free", "vegetarian"}
    ),
    VEGAN(
        "vegan",
        "Conversations about veganism, vegan food, or vegan lifestyle. Includes vegan discussions, vegan food, or vegan lifestyle.",
        new String[]{"vegan", "vegan food", "vegan lifestyle", "plant-based", "veganism", "vegan"}
    ),
    FAST_FOOD(
        "fast_food",
        "Conversations about fast food, quick meals, or fast food restaurants. Includes fast food discussions, quick meals, or fast food chains.",
        new String[]{"fast food", "quick meal", "fast food restaurant", "fast food chain", "quick bite"}
    ),
    FINE_DINING(
        "fine_dining",
        "Conversations about fine dining, upscale restaurants, or gourmet food. Includes fine dining discussions, upscale dining, or gourmet experiences.",
        new String[]{"fine dining", "upscale restaurant", "gourmet", "fine dining experience", "upscale"}
    ),
    BAR(
        "bar",
        "Conversations about bars, drinks, or bar activities. Includes bar discussions, drinks, or bar visits.",
        new String[]{"bar", "drinks", "bar visit", "cocktail", "bar", "drink"}
    ),
    COFFEE(
        "coffee",
        "Conversations about coffee, coffee shops, or coffee drinking. Includes coffee discussions, coffee shops, or coffee preferences.",
        new String[]{"coffee", "coffee shop", "caffeine", "espresso", "latte", "coffee"}
    ),
    TEA(
        "tea",
        "Conversations about tea, tea drinking, or tea preferences. Includes tea discussions, tea drinking, or tea varieties.",
        new String[]{"tea", "tea drinking", "tea variety", "herbal tea", "tea", "tea"}
    ),
    ALCOHOL(
        "alcohol",
        "Conversations about alcohol, alcoholic beverages, or drinking. Includes alcohol discussions, drinks, or alcohol consumption.",
        new String[]{"alcohol", "drink", "alcoholic beverage", "beer", "wine", "alcohol"}
    ),
    WINE(
        "wine",
        "Conversations about wine, wine tasting, or wine preferences. Includes wine discussions, wine tasting, or wine recommendations.",
        new String[]{"wine", "wine tasting", "wine recommendation", "red wine", "white wine", "wine"}
    ),
    BEER(
        "beer",
        "Conversations about beer, beer drinking, or beer preferences. Includes beer discussions, beer varieties, or beer recommendations.",
        new String[]{"beer", "beer drinking", "beer variety", "craft beer", "beer recommendation"}
    ),
    COCKTAIL(
        "cocktail",
        "Conversations about cocktails, cocktail making, or cocktail bars. Includes cocktail discussions, cocktail making, or cocktail bars.",
        new String[]{"cocktail", "cocktail making", "mixed drink", "cocktail bar", "mixology"}
    ),
    
    // Travel Categories
    TRAVEL_PLANNING(
        "travel_planning",
        "Conversations about travel planning, trip planning, or vacation planning. Includes itinerary planning, travel arrangements, or vacation planning.",
        new String[]{"travel plan", "trip plan", "vacation planning", "itinerary", "travel planning"}
    ),
    HOTEL(
        "hotel",
        "Conversations about hotels, hotel bookings, or hotel accommodations. Includes hotel reservations, accommodations, or hotel stays.",
        new String[]{"hotel", "hotel booking", "accommodation", "resort", "hotel stay"}
    ),
    FLIGHT(
        "flight",
        "Conversations about flights, air travel, or flight bookings. Includes flight reservations, airlines, or air travel.",
        new String[]{"flight", "airplane", "airline", "flight booking", "air travel"}
    ),
    VACATION(
        "vacation",
        "Conversations about vacations, vacation planning, or vacation experiences. Includes vacation discussions, holiday planning, or vacation stories.",
        new String[]{"vacation", "holiday", "time off", "vacation planning", "vacation"}
    ),
    BACKPACKING(
        "backpacking",
        "Conversations about backpacking, backpacking trips, or backpacking travel. Includes backpacking discussions, hiking trips, or backpacking adventures.",
        new String[]{"backpacking", "backpack", "backpacking trip", "travel backpack", "hiking"}
    ),
    CRUISE(
        "cruise",
        "Conversations about cruises, cruise ships, or cruise travel. Includes cruise vacations, cruise planning, or cruise experiences.",
        new String[]{"cruise", "cruise ship", "cruise travel", "cruise vacation", "cruise"}
    ),
    ADVENTURE(
        "adventure",
        "Conversations about adventure travel, adventure activities, or adventure experiences. Includes adventure discussions, adventurous activities, or adventure travel.",
        new String[]{"adventure", "adventure travel", "adventure activity", "adventure experience", "adventurous"}
    ),
    SIGHTSEEING(
        "sightseeing",
        "Conversations about sightseeing, tourist attractions, or visiting places. Includes sightseeing discussions, tourist spots, or visiting attractions.",
        new String[]{"sightseeing", "tourist", "attraction", "visit", "sightseeing", "tourist spot"}
    ),
    CULTURE(
        "culture",
        "Conversations about culture, cultural experiences, or cultural activities. Includes cultural discussions, cultural immersion, or cultural experiences.",
        new String[]{"culture", "cultural", "cultural experience", "cultural activity", "cultural immersion"}
    ),
    INTERNATIONAL(
        "international",
        "Conversations about international travel, international trips, or international experiences. Includes international travel discussions, traveling abroad, or international experiences.",
        new String[]{"international", "international travel", "abroad", "overseas", "international", "foreign"}
    ),
    DOMESTIC(
        "domestic",
        "Conversations about domestic travel, local trips, or domestic travel. Includes domestic travel discussions, local trips, or traveling within country.",
        new String[]{"domestic", "local travel", "domestic trip", "within country", "domestic"}
    ),
    BEACH(
        "beach",
        "Conversations about beaches, beach vacations, or beach activities. Includes beach discussions, beach vacations, or beach activities.",
        new String[]{"beach", "beach vacation", "beach trip", "seaside", "beach", "beach resort"}
    ),
    MOUNTAIN(
        "mountain",
        "Conversations about mountains, mountain climbing, or mountain trips. Includes mountain discussions, hiking mountains, or mountain adventures.",
        new String[]{"mountain", "mountain climbing", "hiking", "mountain trip", "peak", "mountain"}
    ),
    CAMPING(
        "camping",
        "Conversations about camping, camping trips, or camping activities. Includes camping discussions, outdoor camping, or camping adventures.",
        new String[]{"camping", "camp", "camping trip", "outdoor camping", "tent", "camping"}
    ),
    ROAD_TRIP(
        "road_trip",
        "Conversations about road trips, driving trips, or road travel. Includes road trip planning, car trips, or road travel.",
        new String[]{"road trip", "driving trip", "road travel", "car trip", "road trip", "drive"}
    ),
    PASSPORT(
        "passport",
        "Conversations about passports, visa, or travel documents. Includes passport applications, visa discussions, or travel documentation.",
        new String[]{"passport", "visa", "travel document", "entry permit", "passport", "visa application"}
    ),
    TOURIST(
        "tourist",
        "Conversations about tourism, tourist activities, or tourist destinations. Includes tourist discussions, tourist destinations, or tourism.",
        new String[]{"tourist", "tourism", "tourist destination", "tourist activity", "tourism", "tourist"}
    ),
    TRAVEL_TIP(
        "travel_tip",
        "Conversations about travel tips, travel advice, or travel recommendations. Includes travel tips, travel advice, or travel suggestions.",
        new String[]{"travel tip", "travel advice", "travel recommendation", "travel suggestion", "travel tip"}
    ),
    SOLO_TRAVEL(
        "solo_travel",
        "Conversations about solo travel, traveling alone, or independent travel. Includes solo travel discussions, traveling alone, or independent trips.",
        new String[]{"solo travel", "travel alone", "independent travel", "solo trip", "travel solo", "solo"}
    ),
    GROUP_TRAVEL(
        "group_travel",
        "Conversations about group travel, traveling with groups, or group trips. Includes group travel discussions, traveling with groups, or group trips.",
        new String[]{"group travel", "travel with group", "group trip", "travel together", "group travel", "group"}
    ),
    LUXURY_TRAVEL(
        "luxury_travel",
        "Conversations about luxury travel, luxury vacations, or upscale travel. Includes luxury travel discussions, premium travel, or upscale vacations.",
        new String[]{"luxury travel", "luxury vacation", "upscale travel", "premium travel", "luxury", "luxury"}
    ),
    BUDGET_TRAVEL(
        "budget_travel",
        "Conversations about budget travel, cheap travel, or affordable travel. Includes budget travel discussions, affordable trips, or cheap travel.",
        new String[]{"budget travel", "cheap travel", "affordable travel", "budget trip", "budget travel", "cheap"}
    ),
    PHOTOGRAPHY_TRAVEL(
        "photography_travel",
        "Conversations about travel photography, photography trips, or photographing while traveling. Includes travel photography discussions, photography trips, or travel photos.",
        new String[]{"travel photography", "photography trip", "photograph travel", "travel photo", "photography", "travel photo"}
    ),
    BUSINESS_TRAVEL(
        "business_travel",
        "Conversations about business travel, work trips, or corporate travel. Includes business travel discussions, work trips, or corporate travel.",
        new String[]{"business travel", "work trip", "corporate travel", "business trip", "business travel", "work trip"}
    ),
    PACKING(
        "packing",
        "Conversations about packing, travel packing, or preparing for trips. Includes packing discussions, travel preparation, or packing for trips.",
        new String[]{"packing", "travel packing", "pack for trip", "pack luggage", "packing", "pack"}
    ),
    LUGGAGE(
        "luggage",
        "Conversations about luggage, suitcases, or travel bags. Includes luggage discussions, suitcase selection, or travel bags.",
        new String[]{"luggage", "suitcase", "travel bag", "baggage", "luggage", "suitcase"}
    ),
    TRANSPORTATION(
        "transportation",
        "Conversations about transportation, getting around, or transport methods. Includes transportation discussions, transport options, or getting around.",
        new String[]{"transportation", "transport", "getting around", "transport method", "transportation", "transport"}
    ),
    TRAVEL_INSURANCE(
        "travel_insurance",
        "Conversations about travel insurance, travel protection, or travel coverage. Includes travel insurance discussions, travel protection, or travel coverage.",
        new String[]{"travel insurance", "travel protection", "travel coverage", "insurance for travel", "travel insurance"}
    ),
    JET_LAG(
        "jet_lag",
        "Conversations about jet lag, time zone differences, or travel fatigue. Includes jet lag discussions, time zone differences, or travel fatigue.",
        new String[]{"jet lag", "time zone", "travel fatigue", "time difference", "jet lag", "time zone"}
    ),
    TRAVEL_MEMORY(
        "travel_memory",
        "Conversations about travel memories, past trips, or travel experiences. Includes travel memory discussions, past trips, or travel stories.",
        new String[]{"travel memory", "past trip", "travel experience", "travel story", "travel memory", "trip memory"}
    );

    private final String name;
    private final String description;
    private final String[] keywords;

    ChatCategory(String name, String description, String[] keywords) {
        this.name = name;
        this.description = description;
        this.keywords = keywords;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String[] getKeywords() {
        return keywords;
    }

    /**
     * Find category by name (case-insensitive)
     */
    public static ChatCategory fromName(String name) {
        if (name == null) return null;
        
        String normalized = name.toLowerCase().trim();
        for (ChatCategory category : values()) {
            if (category.name.equals(normalized)) {
                return category;
            }
            // Also check if any keywords match
            for (String keyword : category.keywords) {
                if (normalized.contains(keyword.toLowerCase())) {
                    return category;
                }
            }
        }
        return null;
    }

    /**
     * Get all category names as a list
     */
    public static java.util.List<String> getAllNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (ChatCategory category : values()) {
            names.add(category.name);
        }
        return names;
    }

    /**
     * Format categories for OpenAI prompt with descriptions
     */
    public static String formatForOpenAI() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available Categories:\n\n");
        
        for (ChatCategory category : values()) {
            sb.append("- ").append(category.name.toUpperCase())
              .append(": ").append(category.description)
              .append(" (Keywords: ").append(String.join(", ", category.keywords))
              .append(")\n");
        }
        
        return sb.toString();
    }

    /**
     * Get categories formatted as a simple list with descriptions
     */
    public static String getFormattedCategoryList() {
        StringBuilder sb = new StringBuilder();
        for (ChatCategory category : values()) {
            sb.append(String.format("\"%s\" - %s\n", 
                category.name, category.description));
        }
        return sb.toString();
    }
}

