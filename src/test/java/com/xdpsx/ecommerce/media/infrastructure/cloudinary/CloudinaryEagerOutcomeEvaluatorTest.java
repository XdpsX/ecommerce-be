package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.media.domain.MediaPurpose;

class CloudinaryEagerOutcomeEvaluatorTest {
    private final CloudinaryEagerOutcomeEvaluator evaluator = new CloudinaryEagerOutcomeEvaluator();

    @Test
    void evaluate_ShouldRequireEveryExpectedProductVariantBeforeReady() {
        List<String> expected = CloudinaryMediaTransformations.eagerStringsFor(MediaPurpose.PRODUCT_IMAGE);
        assertEquals(2, expected.size());

        assertEquals(
                CloudinaryEagerOutcomeEvaluator.Outcome.INCONCLUSIVE,
                evaluator.evaluate(notification(List.of(success(expected.getFirst()))), MediaPurpose.PRODUCT_IMAGE));
        assertEquals(
                CloudinaryEagerOutcomeEvaluator.Outcome.READY,
                evaluator.evaluate(
                        notification(expected.stream()
                                .map(CloudinaryEagerOutcomeEvaluatorTest::success)
                                .toList()),
                        MediaPurpose.PRODUCT_IMAGE));
    }

    @Test
    void evaluate_ShouldNotTrustUnknownTransformationOrArbitraryUrlAsSuccess() {
        String expected = CloudinaryMediaTransformations.eagerStringsFor(MediaPurpose.CATEGORY_IMAGE)
                .getFirst();
        var arbitrary = new CloudinaryEagerNotification.EagerResult(
                expected, "https://example.test/fake.jpg", null, null, null, null, "jpg", 10L, 100, 100);
        var unknown = new CloudinaryEagerNotification.EagerResult(
                "c_limit,w_1",
                "https://res.cloudinary.com/demo/a.jpg",
                "success",
                null,
                null,
                null,
                "jpg",
                10L,
                100,
                100);

        assertEquals(
                CloudinaryEagerOutcomeEvaluator.Outcome.INCONCLUSIVE,
                evaluator.evaluate(notification(List.of(arbitrary, unknown)), MediaPurpose.CATEGORY_IMAGE));
    }

    @Test
    void evaluate_ShouldHonorExplicitFailureOfExpectedTransformation() {
        String expected = CloudinaryMediaTransformations.eagerStringsFor(MediaPurpose.BRAND_LOGO)
                .getFirst();
        var failed = new CloudinaryEagerNotification.EagerResult(
                expected, null, "failed", null, "provider reason", null, null, null, null, null);

        assertEquals(
                CloudinaryEagerOutcomeEvaluator.Outcome.FAILED,
                evaluator.evaluate(notification(List.of(failed)), MediaPurpose.BRAND_LOGO));
    }

    private static CloudinaryEagerNotification notification(List<CloudinaryEagerNotification.EagerResult> eager) {
        return new CloudinaryEagerNotification(
                "eager",
                "batch-1",
                "product/media-1",
                "asset-1",
                "2026-10-15T00:00:00Z",
                "success",
                null,
                null,
                null,
                null,
                eager);
    }

    private static CloudinaryEagerNotification.EagerResult success(String transformation) {
        return new CloudinaryEagerNotification.EagerResult(
                transformation,
                "https://res.cloudinary.com/demo/image/upload/transformed.jpg",
                null,
                null,
                null,
                null,
                "jpg",
                1024L,
                600,
                600);
    }
}
