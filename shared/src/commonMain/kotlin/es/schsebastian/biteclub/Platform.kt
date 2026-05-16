package es.schsebastian.biteclub

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform