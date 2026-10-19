package com.xdpsx.ecommerce.media.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.media.application.storage.MediaUrlGenerator;
import com.xdpsx.ecommerce.media.application.storage.MediaVariant;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

@ExtendWith(MockitoExtension.class)
class MediaUrlResolverTest {
    @Mock
    private MediaUrlGenerator mediaUrlGenerator;

    @Test
    void resolve_shouldReturnNamedVariantWhenProcessingIsReady() {
        MediaUrlResolver resolver = new MediaUrlResolver(mediaUrlGenerator);
        Media media = media(MediaProcessingStatus.READY);
        when(mediaUrlGenerator.generateVariants("external-product", MediaPurpose.PRODUCT_IMAGE))
                .thenReturn(Map.of("productCard", "https://cdn.example.test/card.jpg"));

        String result = resolver.resolve(media, MediaVariant.PRODUCT_CARD);

        assertThat(result).isEqualTo("https://cdn.example.test/card.jpg");
    }

    @ParameterizedTest
    @MethodSource("nonReadyStatuses")
    void resolve_shouldReturnOriginalUrlUntilProcessingIsReady(MediaProcessingStatus status) {
        MediaUrlResolver resolver = new MediaUrlResolver(mediaUrlGenerator);

        String result = resolver.resolve(media(status), MediaVariant.PRODUCT_CARD);

        assertThat(result).isEqualTo("https://cdn.example.test/original.jpg");
        verifyNoInteractions(mediaUrlGenerator);
    }

    @Test
    void resolve_shouldRejectVariantForAnotherPurpose() {
        MediaUrlResolver resolver = new MediaUrlResolver(mediaUrlGenerator);

        assertThatThrownBy(() -> resolver.resolve(media(MediaProcessingStatus.READY), MediaVariant.BRAND_LOGO))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mediaUrlGenerator);
    }

    private static Stream<MediaProcessingStatus> nonReadyStatuses() {
        return Stream.of(MediaProcessingStatus.PROCESSING, MediaProcessingStatus.FAILED);
    }

    private static Media media(MediaProcessingStatus status) {
        return Media.builder()
                .id("media-id")
                .externalId("external-product")
                .url("https://cdn.example.test/original.jpg")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .processingStatus(status)
                .build();
    }
}
