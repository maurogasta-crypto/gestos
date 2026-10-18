package com.maurogasta.gestos

// ─────────────────────────────────────────────────────────────────────────────
// Manual.kt — Cómo se usa, en pocas palabras (pedido de Mauro, 18-oct-2026:
// «un pequeño manual que explique brevemente cómo se usa y la idea de cada
// parte, lo más simple y claro posible»).
//
// Es el MISMO texto que «Cómo se usa» de la página (gestos.html): si cambia
// uno, cambia el otro. Y no puede mentir: el banco comprueba que nombre cada
// botón de la pantalla, y si un botón se renombra, el manual también.
// ─────────────────────────────────────────────────────────────────────────────

object Manual {
    val partes = listOf(
        "La idea" to
            "Un instrumento que se toca con las manos frente a la cámara, como un theremin " +
            "con colores. Una mano dice qué nota; la otra, cuánto suena.",
        "Antes de empezar" to
            "Teléfono parado, a un metro, con buena luz. Que se vean las dos manos y que " +
            "no haya otras manos atrás.",
        "Mano izquierda: la nota" to
            "Subila y la nota sube; bajala y baja. Va sin cortes, como un dedo que corre por " +
            "la cuerda de un violín. Cada color es una nota; las líneas marcadas son las " +
            "notas de la escala, para apuntar.",
        "Mano derecha: el volumen" to
            "Arriba suena fuerte, abajo no suena. Cerrar el puño calla en el acto. Sin las " +
            "dos manos a la vista, no suena nada.",
        "Vibrato" to
            "Juntá el pulgar y el índice de la mano de la nota: la nota tiembla. Cuanto más " +
            "apretado, más.",
        "Lo que no hace nada" to
            "Acercar o alejar las manos, o moverlas de costado. Sólo cuenta subir y bajar.",
        "Los botones" to
            "Silencio: calla todo.\n" +
            "Manos cambiadas: la nota pasa a la mano derecha y el volumen a la izquierda.\n" +
            "Ajustes: la tónica, la escala (las líneas marcadas), cuántas octavas y el " +
            "imán. Con el imán en cero la nota corre libre; al máximo cae justo en las " +
            "notas de la escala, como un teclado.",
        "La línea gris" to
            "Cuánto tarda cada parte (cámara, reconocer la mano, sonido). No hace falta " +
            "para tocar: sirve para mejorar el instrumento. Si algo anda lento, mandá una " +
            "captura de esa línea.",
    )

    // Los textos de los botones, para que el banco compare contra el manual.
    val BOTONES = listOf("Silencio", "Manos cambiadas", "Ajustes")
}
