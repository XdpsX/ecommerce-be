package com.xdpsx.ecommerce.auth.infrastructure.security.oauth2;

import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.auth.infrastructure.security.CustomUserDetails;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;
    private final List<OAuth2UserInfoExtractor> oauth2UserInfoExtractors;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Optional<OAuth2UserInfoExtractor> oAuth2UserInfoExtractorOptional = oauth2UserInfoExtractors.stream()
                .filter(oAuth2UserInfoExtractor -> oAuth2UserInfoExtractor.accepts(userRequest))
                .findFirst();
        if (oAuth2UserInfoExtractorOptional.isEmpty()) {
            throw new InternalAuthenticationServiceException("The OAuth2 provider is not supported yet");
        }

        CustomUserDetails customUserDetails =
                oAuth2UserInfoExtractorOptional.get().extractUserInfo(oAuth2User);
        User user = upsertUser(customUserDetails);
        customUserDetails.setId(user.getId());
        return customUserDetails;
    }

    private User upsertUser(CustomUserDetails customUserDetails) {
        Optional<User> userOptional = userRepository.findByEmail(customUserDetails.getUsername());
        User user;
        if (userOptional.isEmpty()) {
            user = new User();
            user.setName(customUserDetails.getName());
            user.setEmail(customUserDetails.getUsername());
            user.setAvatar(customUserDetails.getAvatar());
            user.setAuthProvider(customUserDetails.getAuthProvider());
            user.setRole(Role.USER);
            return userRepository.save(user);
        } else {
            return userOptional.get();
        }
    }
}
