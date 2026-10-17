package codequest.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import codequest.Main;

/** Port of HowToPlayPanel — 4 full-page guide images with prev/next/back nav. */
public class HowToPlayScreen extends BaseScreen {

    private static final int TOTAL_PAGES = 4;

    private int currentPage = 0;
    private final TextureRegionDrawable[] pages = new TextureRegionDrawable[TOTAL_PAGES];
    private final Image pageImage;
    private final ImageButton prevBtn;
    private final ImageButton nextBtn;
    private final Label pageIndicator;

    /** onBack is what the Back button does (the guide can be opened before logging in). */
    public HowToPlayScreen(Main game, Runnable onBack) {
        for (int i = 0; i < TOTAL_PAGES; i++) {
            pages[i] = new TextureRegionDrawable(new TextureRegion(texture(Art.howToPlayPage(i + 1))));
        }

        pageImage = new Image(pages[0]);
        pageImage.setBounds(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        stage.addActor(pageImage);

        Image titleGraphic = new Image(new TextureRegionDrawable(new TextureRegion(texture(Art.TITLE_HOW_TO_PLAY))));
        float titleWidth = 700f;
        float titleHeight = titleWidth * (titleGraphic.getDrawable().getMinHeight() / titleGraphic.getDrawable().getMinWidth());
        placeTopLeft(titleGraphic, (VIRTUAL_WIDTH - titleWidth) / 2f, 90f - titleHeight / 2f, titleWidth, titleHeight);
        stage.addActor(titleGraphic);

        pageIndicator = addCenteredTitle((currentPage + 1) + " / " + TOTAL_PAGES, 140f, 1.4f,
                new Color(1f, 215 / 255f, 0, 1f));

        float btnWidth = 260f;
        float btnHeight = 75f;
        float topY = 40f;
        float backX = 60f;
        float prevX = VIRTUAL_WIDTH - 620f;
        float nextX = VIRTUAL_WIDTH - 320f;

        ImageButton backBtn = imageButton(Art.def("back_small"), Art.hover("back_small"));
        placeTopLeft(backBtn, backX, topY, btnWidth, btnHeight);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onBack.run();
            }
        });
        stage.addActor(backBtn);

        prevBtn = imageButton(Art.def("prev"), Art.hover("prev"));
        placeTopLeft(prevBtn, prevX, topY, btnWidth, btnHeight);
        prevBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (currentPage > 0) {
                    currentPage--;
                    updatePage();
                }
            }
        });
        stage.addActor(prevBtn);

        nextBtn = imageButton(Art.def("next"), Art.hover("next"));
        placeTopLeft(nextBtn, nextX, topY, btnWidth, btnHeight);
        nextBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (currentPage < TOTAL_PAGES - 1) {
                    currentPage++;
                    updatePage();
                }
            }
        });
        stage.addActor(nextBtn);

        updatePage();
    }

    private void updatePage() {
        pageImage.setDrawable(pages[currentPage]);
        pageIndicator.setText((currentPage + 1) + " / " + TOTAL_PAGES);
        prevBtn.setVisible(currentPage > 0);
        nextBtn.setVisible(currentPage < TOTAL_PAGES - 1);
    }
}
