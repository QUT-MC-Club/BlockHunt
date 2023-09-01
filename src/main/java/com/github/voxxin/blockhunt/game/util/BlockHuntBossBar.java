package com.github.voxxin.blockhunt.game.util;

import com.github.voxxin.blockhunt.game.util.ext.BossBarWidgetExt;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.CommandBossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class BlockHuntBossBar extends CommandBossBar {
    private final ServerBossBar bossBar;

    public BlockHuntBossBar(Text title) {
        super(Identifier.of("blockhunt", String.valueOf(title)), title);
        Text bossbarTitle = Text.literal("")
                .append(title);

        this.bossBar = new ServerBossBar(bossbarTitle, BossBar.Color.RED, BossBar.Style.NOTCHED_20);

    }

    public static class HideTimeBossbar extends CommandBossBar {
        private final ServerBossBar widget;
        private float timeUntilHidden = 1F;

        private boolean hidden = false;

        public HideTimeBossbar() {
            super(Identifier.of("blockhunt", "blockhunt.bossbar.not_hidden"), Text.translatable("blockhunt.bossbar.not_hidden"));

            Text bossbarTitle = Text.literal("")
                    .append(Text.translatable("bossbar.blockhunt.not_hidden",
                                            Text.literal(String.valueOf(this.timeUntilHidden)).formatted(Formatting.YELLOW)
                                    )
                                    .formatted(Formatting.WHITE)
                    );
            this.widget = new ServerBossBar(bossbarTitle, BossBar.Color.RED, BossBar.Style.NOTCHED_20);
        }

        public void addPlayer(ServerPlayerEntity player) {
            ((BossBarWidgetExt) widget).blockHunt$addSinglePlayer(player);
        }

        public void update(boolean moved) {
            if (moved) {
                this.timeUntilHidden = 1F;
            } else if (timeUntilHidden > 0) {
                this.timeUntilHidden -= 0.05F;
            }

            if (this.timeUntilHidden > 0F) {
                this.widget.setColor(BossBar.Color.RED);
                this.widget.setStyle(BossBar.Style.NOTCHED_20);
                this.widget.setPercent(this.timeUntilHidden);

                Text newTitle = Text.literal("")
                        .append(Text.translatable("bossbar.blockhunt.not_hidden",
                                                Text.literal(String.valueOf(this.timeUntilHidden)).formatted(Formatting.YELLOW)
                                        )
                                        .formatted(Formatting.WHITE)
                        );

                hidden = false;
                this.widget.setName(newTitle);
            } else {
                this.widget.setPercent(1.0F);
                this.widget.setColor(BossBar.Color.PINK);
                this.widget.setStyle(BossBar.Style.PROGRESS);
                this.widget.setName(Text.translatable("bossbar.blockhunt.hidden"));
                hidden = true;
            }
        }

        public float getTimeUntilHidden() {
            if (hidden) return 0F;
            return this.timeUntilHidden;
        }

        public void remove() {
            this.widget.setVisible(false);
        }
    }
}
