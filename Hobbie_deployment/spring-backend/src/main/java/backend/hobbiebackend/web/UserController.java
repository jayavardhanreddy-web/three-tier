package backend.hobbiebackend.web;


import backend.hobbiebackend.handler.NotFoundException;
import backend.hobbiebackend.model.dto.AppClientSignUpDto;
import backend.hobbiebackend.model.dto.BusinessRegisterDto;
import backend.hobbiebackend.model.dto.UpdateAppClientDto;
import backend.hobbiebackend.model.dto.UpdateBusinessDto;
import backend.hobbiebackend.model.entities.AppClient;
import backend.hobbiebackend.model.entities.BusinessOwner;
import backend.hobbiebackend.model.entities.UserEntity;
import backend.hobbiebackend.model.entities.enums.UserRoleEnum;
import backend.hobbiebackend.model.jwt.JwtRequest;
import backend.hobbiebackend.model.jwt.JwtResponse;
import backend.hobbiebackend.security.HobbieUserDetailsService;
import backend.hobbiebackend.service.HobbyService;
import backend.hobbiebackend.service.NotificationService;
import backend.hobbiebackend.service.UserService;
import backend.hobbiebackend.utility.JWTUtility;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.mail.MailException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;


@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "https://hobbie-ui-web.herokuapp.com")
public class UserController {



    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    private final NotificationService notificationService;
    private final JWTUtility jwtUtility;
    private final AuthenticationManager authenticationManager;
    private final HobbieUserDetailsService hobbieUserDetailsService;

    @Autowired
    public UserController(UserService userService, PasswordEncoder passwordEncoder, NotificationService notificationService, JWTUtility jwtUtility, AuthenticationManager authenticationManager, HobbieUserDetailsService hobbieUserDetailsService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.jwtUtility = jwtUtility;

        this.authenticationManager = authenticationManager;
        this.hobbieUserDetailsService = hobbieUserDetailsService;
    }



    @PostMapping("/signup/")
    public ResponseEntity<?> signup(@RequestBody AppClientSignUpDto user){
        System.out.println(user);

        if( this.userService.userExists(user.getUsername(), user.getEmail())) {
            throw new RuntimeException("Username or email address already in use.");
        }

        AppClient client = this.userService.register(user);
        return new ResponseEntity<AppClient>(client, HttpStatus.CREATED);
    }


    @PostMapping("/register-business/")
    public ResponseEntity<?> registerBusiness(@RequestBody BusinessRegisterDto business){


    if(this.userService.businessExists(business.getBusinessName() ) || this.userService.userExists(business.getUsername(), business.getEmail())){
        throw new RuntimeException("Username or email address already in use.");
    }

        BusinessOwner businessOwner = this.userService.registerBusiness(business);

        return new ResponseEntity<BusinessOwner>(businessOwner, HttpStatus.CREATED);
    }   




    @GetMapping("/show-client-details/{username}/")
    public AppClient showUserDetails(@PathVariable String username) {
            return  this.userService.findAppClientByUsername(username);
    }


    @GetMapping("/show-business-details/{username}/")
    public BusinessOwner showBusinessDetails(@PathVariable String username) {
        return  this.userService.findBusinessByUsername(username);
    }

    @PutMapping("/update-user")
        public ResponseEntity<?>  updateUser(@RequestBody UpdateAppClientDto user) {

                    AppClient client = this.userService.findAppClientById(user.getId());

                    client.setPassword(this.passwordEncoder.encode(user.getPassword()));
                    client.setGender(user.getGender());
                    client.setFullName(user.getFullName());

                    this.userService.saveUpdatedUserClient(client);
            return new ResponseEntity<AppClient>(client, HttpStatus.CREATED);

    }
    @PostMapping ("/change-password/")
    public ResponseEntity<?>  changePassword(@RequestBody String e) throws UnsupportedEncodingException {
        String email = URLDecoder.decode(e, "UTF-8");
        email = email.substring(0, email.length()-1);
        UserEntity userByEmail = this.userService.findUserByEmail(email);

        if(userByEmail == null){
            throw new NotFoundException("User not found");
        }
        else {
            this.notificationService.sendNotification(userByEmail);
        }
        return new ResponseEntity<>(userByEmail, HttpStatus.OK);
    }
    @PostMapping("/change-password-new/")
    public ResponseEntity<?>  setUpNewPassword(@RequestParam Long id, String password) {
        UserEntity userById = this.userService.findUserById(id);
        userById.setPassword(this.passwordEncoder.encode(password));
        this.userService.saveUserWithUpdatedPassword(userById);
        return new ResponseEntity<UserEntity>(userById,HttpStatus.CREATED);
    }

    @PutMapping("/update-business/")
    public ResponseEntity<?>  updateBusiness(@RequestBody UpdateBusinessDto business) {

        BusinessOwner businessOwner = this.userService.findBusinessOwnerById(business.getId());
        if(this.userService.businessExists(business.getBusinessName()) && (!businessOwner.getBusinessName().equals(business.getBusinessName()))){
            throw new RuntimeException("Business name already in use.");
        }
                businessOwner.setBusinessName(business.getBusinessName());
                businessOwner.setPassword(this.passwordEncoder.encode(business.getPassword()));
                businessOwner.setAddress(business.getAddress());
                this.userService.saveUpdatedUser(businessOwner);
    
        return new ResponseEntity<BusinessOwner>(businessOwner, HttpStatus.CREATED);

    }
    @DeleteMapping("/delete-user/{id}/")
    public ResponseEntity<Long> deleteUser(@PathVariable Long id){
        boolean isRemoved = this.userService.deleteUser(id);

        if (!isRemoved) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(id, HttpStatus.OK);
    }

    @PostMapping("/authenticate/")
    public JwtResponse authenticate(@RequestBody JwtRequest jwtRequest) throws Exception{

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            jwtRequest.getUsername(),
                            jwtRequest.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            throw new Exception("INVALID_CREDENTIALS", e);
        }

        final UserDetails userDetails
                = hobbieUserDetailsService.loadUserByUsername(jwtRequest.getUsername());

        final String token =
                jwtUtility.generateToken(userDetails);

        return  new JwtResponse(token);
    }


    @PostMapping("/login/{username}")
    public String logInUser(@PathVariable String username) {
        UserEntity userByUsername = this.userService.findUserByUsername(username);
        if (userByUsername.getRoles().stream()
                .anyMatch(u-> u.getRole().equals(UserRoleEnum.USER))) {
            return  "USER";
        }
        else if(userByUsername.getRoles().stream()
                .anyMatch(u-> u.getRole().equals(UserRoleEnum.BUSINESS_USER))){
            return  "BUSINESS_USER";
        }
        return null;
    }
}


