package com.blogspot.vsvydenko.yafotki_muzei;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;

import java.util.Random;

import retrofit.ErrorHandler;
import retrofit.RestAdapter;
import retrofit.RetrofitError;

import com.blogspot.vsvydenko.yafotki_muzei.YandexFotkiServiceInterface.PhotosResponse;
import com.blogspot.vsvydenko.yafotki_muzei.YandexFotkiServiceInterface.Photo;

/**
 * Created by vsvydenko on 26.02.14.
 */
public class YandexFotkiArtSource extends RemoteMuzeiArtSource {

    public static String ACTION_UPDATE  = "ACTION_UPDATE";
    public static String SOURCE_NAME    = "YandexFotkiArtSource";
    public static String POPULAR        = "POPULAR";
    public static String POD            = "POD";

    PhotosResponse response;

    public YandexFotkiArtSource() {
        super(SOURCE_NAME);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setUserCommands(BUILTIN_COMMAND_ID_NEXT_ARTWORK);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            super.onHandleIntent(intent);
            return;
        }

        String action = intent.getAction();
        if (ACTION_UPDATE.equals(action)) {
            scheduleUpdate(System.currentTimeMillis() + 1000);
        }

        super.onHandleIntent(intent);
    }

    @Override
    protected void onTryUpdate(int reason) throws RetryException {

        if (PreferenceHelper.isWiFiChecked() && !Utils.isWiFiOn(this)) {
            return;
        }

        String currentToken = (getCurrentArtwork() != null) ? getCurrentArtwork().getToken() : null;

        RestAdapter restAdapter = new RestAdapter.Builder()
                .setServer("http://api-fotki.yandex.ru/api")
                .setErrorHandler(new ErrorHandler() {
                    @Override
                    public Throwable handleError(RetrofitError cause) {
                        if (cause.getResponse() == null)
                            return new RetryException();
                        scheduleUpdate(System.currentTimeMillis() + PreferenceHelper.getInterval());
                        return cause;
                    }
                })
                .build();

        YandexFotkiServiceInterface yandexFotkiService = restAdapter.
                create(YandexFotkiServiceInterface.class);

        if (PreferenceHelper.getSourceUrl().equals(POD)) {
            response = yandexFotkiService.getPODPhoto(Utils.getDate());
        } else {
            response = yandexFotkiService.getTopPhotos(Utils.getDate());
        }


        if (response == null || response.entries == null) {
            throw new RetryException();
        }

        if (response.entries.isEmpty()) {
            scheduleUpdate(System.currentTimeMillis() + PreferenceHelper.getInterval());
            return;
        }

        Random random = new Random();
        Photo photo;
        String token;

        while (true) {
            photo = response.entries.get(random.nextInt(response.entries.size()));
            token = photo.id;
            if (response.entries.size() <= 1 || !TextUtils.equals(token, currentToken)) {
                break;
            }
        }

        publishArtwork(new Artwork.Builder()
                .title(photo.title)
                .byline(photo.author)
                .imageUri(Uri.parse(photo.img.XXXL.href))
                .token(token)
                .viewIntent(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(photo.links.alternate)))
                .build()
        );

        scheduleUpdate(System.currentTimeMillis() + PreferenceHelper.getInterval());
    }
}
