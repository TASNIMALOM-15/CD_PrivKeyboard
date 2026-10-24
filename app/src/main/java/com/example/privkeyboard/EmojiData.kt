package com.example.privkeyboard

object EmojiData {

    val tabIcons = listOf("🕘", "😀", "👋", "\u2764\uFE0F", "🐶", "🍔", "\u26BD", "💡")

    private fun list(s: String): List<String> = s.split(" ").filter { it.isNotEmpty() }

    /** Categories for tabs 1..7 (tab 0 = recents). */
    val categories: List<List<String>> = listOf(
        list(
            "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 😘 😗 😙 😚 😋 😛 😜 😝 🤑 🤗 🤔 🤐 😐 😑 😶 😏 😒 🙄 😬 " +
                "😔 😪 😴 😷 🤒 🤕 😵 😎 🤓 😕 😟 🙁 😮 😯 😲 😳 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 😤 😡 😠 " +
                "😈 👿 💀 💩 🤡 👻 👽 🤖 🤠"
        ),
        list(
            "👍 👎 👊 ✊ 👌 ✋ 👏 🙌 👐 🙏 🤝 💪 👈 👉 👆 👇 🤞 🤘 👋 🤙 🙋 🙆 🙅 🤷 🤦 🙇 💁 👀 👂 👃 👄 👅 👣 " +
                "\u270C\uFE0F \u261D\uFE0F \u270D\uFE0F"
        ),
        list(
            "\u2764\uFE0F 💛 💚 💙 💜 🖤 💔 \u2763\uFE0F 💕 💞 💓 💗 💖 💘 💝 💯 💢 💥 💫 💦 💨 💤 ✨ \u2B50 🌟 🔥 🎉 🎊 " +
                "🎈 🎁 🏆 \u2705 \u274C \u2753 \u2757 \u26A0\uFE0F \u2714\uFE0F 💬 💭 🔔 🔒 🔑"
        ),
        list(
            "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐺 🐴 🦄 🐝 🐛 🦋 🐌 🐞 " +
                "🐜 🐢 🐍 🦂 🐙 🦀 🐠 🐟 🐬 🐳 🐘 🐪 🐫 🌸 🌹 🌺 🌻 🌼 🌷 🌱 🌲 🌳 🌴 🌵 🍀 🍁 🍂 🍃 🌈 " +
                "\u2600\uFE0F \u2601\uFE0F \u26A1 \u2744\uFE0F 🌙 🌊"
        ),
        list(
            "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🍍 🍅 🍆 🌽 🍞 🧀 🍖 🍗 🍔 🍟 🍕 🌭 🌮 🌯 🍳 🍲 🍿 🍱 🍙 🍚 🍛 🍜 " +
                "🍝 🍣 🍤 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 🍮 \u2615 🍵 🍶 🍺 🍻 🍷 🍹 🍴"
        ),
        list(
            "\u26BD 🏀 🏈 \u26BE 🎾 🏐 🏉 🎱 🏓 🏸 🎯 🎮 🎲 🎵 🎶 🎤 🎧 🎸 🎹 🎺 🎻 🎬 🎨 🏊 🚴"
        ),
        list(
            "🚗 🚕 🚌 🚓 🚑 🚒 🚚 🚲 🏍 \u2708\uFE0F 🚀 🚁 \u26F5 🚢 🏠 🏢 🏥 🏦 🏨 🌍 📱 💻 \u2328\uFE0F 🖥 📷 📺 📻 " +
                "\u23F0 \u231A 💡 🔋 🔌 💰 💳 💎 🔧 🔨 🔓 📌 📎 \u2702\uFE0F 📝 📚 📖 📧 📦"
        )
    )
}
