<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="JNakau4V7q0aBPrarqtdYD3wFFOTLtAf9SVfAOFU" />
        <title>    Standing On The Rock - Login
</title>
    <link rel="icon" type="image/x-icon" href="https://standingontherock.com/public/admin_assets/img/favicon.ico">
    <link href="https://standingontherock.com/public/frontend_assets/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://standingontherock.com/public/frontend_assets/css/font-awesome-all.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.7.2/css/all.min.css">
    <link href="https://unpkg.com/aos@2.3.1/dist/aos.css" rel="stylesheet">
    <link rel="stylesheet" type="text/css"
        href="https://cdnjs.cloudflare.com/ajax/libs/slick-carousel/1.9.0/slick.min.css" />
    <link rel="stylesheet" type="text/css"
        href="https://cdnjs.cloudflare.com/ajax/libs/slick-carousel/1.9.0/slick.min.css" />
    <link rel="stylesheet" href="https://standingontherock.com/public/frontend_assets/css/owl.carousel.min.css">
    <link rel="stylesheet" href="https://standingontherock.com/public/frontend_assets/css/style.css?version=250711061154">
    <link rel="stylesheet" href="https://standingontherock.com/public/frontend_assets/css/responsive.css?version=250711061154">
    <link rel="stylesheet" type="text/css"
        href="https://cdnjs.cloudflare.com/ajax/libs/toastr.js/latest/toastr.min.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/sweetalert2@11/dist/sweetalert2.min.css">
    </head>

<body class="d-flex flex-column min-vh-100">
    <section id="loading">
        <div id="loading-content"></div>
    </section>
    <div class="top-bar">
    <div class="container">
        <div class="top-rivon align-items-center">
            <div class="buttons-item only-desktop">
                <div class="logo-wrap">
                    <a href="https://standingontherock.com"><img
                            src="https://standingontherock.com/public/storage/logos/T5HCCTT4cVUSKTF9IJZCIpc2UmpLLBIpjfJGT1Ib.jpg"
                            alt=""></a>
                </div>
            </div>
            <!--<div class="buttons-item text-center flex-grow-1">-->
            <!--    <a href="https://standingontherock.com/courses" class="btn">Available Courses</a>-->
            <!--</div>-->
            <div class="buttons-item only-desktop av-course">
                <h6><a href="https://standingontherock.com/courses">Available Courses</a></h6>
            </div>
            <div class="buttons-item text-end">
                <ul>
                                        
                                        <li class="nav-item dropdown dropdown-niv2">
                        <a class="nav-link dropdown-toggle" href="#" data-bs-toggle="dropdown"
                            data-bs-auto-close="outside"><span class="user-icon"><i
                                    class="fa-solid fa-user"></i></span></a>
                        <ul class="dropdown-menu dropdown-menu-niv2 shadow">
                                                            <li><a class="dropdown-item" href="https://standingontherock.com/login">Log in</a></li>
                                <li><a class="dropdown-item" href="https://standingontherock.com/register">Register</a></li>
                                                    </ul>
                    </li>
                </ul>
            </div>
            <!-- mobile humburgar  -->
            <div class="hamburger">
                <span class="line"></span>
                <span class="line"></span>
                <span class="line"></span>
            </div>
            <!-- mobile humburgar  -->

        </div>
    </div>
</div>
<header class="header page-header">
    <div class="container">
        <div class="row-wrap">
            <div class="left-part">
                <div class="logo-wrap">
                    <a href="https://standingontherock.com"><img
                            src="https://standingontherock.com/public/storage/logos/T5HCCTT4cVUSKTF9IJZCIpc2UmpLLBIpjfJGT1Ib.jpg"
                            alt=""></a>
                </div>
            </div>
            <div class="for-desktop">
                <ul>
                    <li class="active"><a href="https://standingontherock.com">Home</a></li>
                </ul>
            </div>
            <div class="right-part">
                <div class="nav-wrap">
                    <ul class="nav-list">
                        <li class=""><a href="https://standingontherock.com">Home</a></li>
                                                                                    <li class="">
                                    <a href="https://standingontherock.com/the-refuge">The Refuge
                                        <p>Private Asset Protection</p>
                                    </a>
                                </li>
                                                            <li class="">
                                    <a href="https://standingontherock.com/the-remedy">The Remedy
                                        <p>Equity Jurisprudence</p>
                                    </a>
                                </li>
                                                            <li class="">
                                    <a href="https://standingontherock.com/the-rock">The Rock
                                        <p>Foundational Wisdom</p>
                                    </a>
                                </li>
                                                                            <li class=""><a
                                href="https://standingontherock.com/about-us">About Us</a></li>
                    </ul>
                </div>
            </div>
            <div class="for-desktop">
                <ul>
                    <li><a href="https://standingontherock.com/about-us">About Us</a></li>
                </ul>
            </div>



        </div>
    </div>
</header>
    <div class="overlay"></div>
        <section class="login-sec common-padd">
        <div class="container">
            <div class="row justify-content-center">
                <div class="col-lg-6">
                    <div class="login-form shadow p-4">
                        <h3 class="text-center mb-4">Login to Your Account</h3>

                        
                        <form id="login-form">
                            <input type="hidden" name="_token" value="JNakau4V7q0aBPrarqtdYD3wFFOTLtAf9SVfAOFU" autocomplete="off">                            <input type="hidden" name="redirect" value="">

                            <div class="mb-3">
                                <label class="form-label">Email Address</label>
                                <input type="email" class="form-control" name="email" required>
                                <span class="text-danger" id="email-error"></span>
                            </div>

                            <div class="mb-3">
                                <label class="form-label">Password</label>
                                <input type="password" class="form-control" name="password" required>
                                <span class="text-danger" id="password-error"></span>
                            </div>

                            <button type="submit" class="red_btn w-100" id="login-btn">
                                <span class="btn-text">Login</span>
                                <span class="btn-loader d-none">
                                    <i class="fas fa-spinner fa-spin"></i> Logging in...
                                </span>
                            </button>
                        </form>

                        <div class="text-center link mt-3">
                            <p>Don't have an account? <a href="https://standingontherock.com/register">Register here</a></p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>

    
    <footer class="common-padd pb-0 mt-auto">
    <div class="container">
        <div class="row">
            <div class="col-lg-4 order-lg-3 order-md-1 order-sm-1">
                <div class="f-logo">
                    <img src="https://standingontherock.com/public/storage/logos/sTQ0newjYRBLvsa6ZSbwjFyN3IRmLJNNWGQWKtCZ.jpg"
                        alt="">
                </div>
                <div class="news-letter-sec">
                    <h6>Newsletter</h6>
                    <p>Subscribe to our newsletter for the latest updates.
                    </p>
                    <form id="newsletter-form">
                        <input class="newsletter-name-input" type="text" name="name" placeholder="Enter your name">
                        <input type="email" name="email" placeholder="Enter your email address">
                        <button id="newsletter-submit" type="submit" class="bt">Subscribe</button>
                    </form>
                    <p class="newsletter-message"></p>
                </div>
            </div>
            <div class="col-lg-5 order-lg-1 order-md-2 order-sm-2">
                <div class="f-text">
                    <p>Standing on the Rock Ministry and Trust is committed to truth, honor, and education. Scripture tells us that: “Our people perish for lack of knowledge.” As we sentient, moral beings face the possibility of increasing tyrannical measures, it is more important than ever to know how to stand and speak up for our rights and wellbeing, as well as protect our assets for ourselves and our future generations.

<div class="contact-info">
                    <div class="info-area">
                        <h6 class="
                        text-white">We welcome your questions or comments.</h6>
                        <h4>Contact us at</h4>
                        <ul>
                            <li><a href="javascript:void(0);"><span><i class="fa-solid fa-envelope"></i></span>
                                    standingontherock@tuta.com</a>
                                                                                                 <span class="text-white ms-1"><font color="#000000">Or</font> </span><span class="ms-2"> <a target="_blank" href="https://t.me/vitiate0"><span><i class="fa-brands fa-telegram"></i></span> https://t.me/vitiate0</a></span>

                                                                                </li>

                            <li hidden=""><a href="javascript:void(0);"><span><i class="fa-solid fa-phone"></i></span>
                                    &amp;nbsp;</a>
                            </li>
                        </ul>
                    </div>

                </div>
                    </p>
                </div>


            </div>
            <div class="col-lg-3 order-lg-2 order-md-3 order-sm-3">
                <div class="info-area quick-link">
                    <h4>Links</h4>
                    <ul>
                                                                                    <li class="">
                                    <a href="https://standingontherock.com/the-refuge">The Refuge</a>
                                </li>
                                                            <li class="">
                                    <a href="https://standingontherock.com/the-remedy">The Remedy</a>
                                </li>
                                                            <li class="">
                                    <a href="https://standingontherock.com/the-rock">The Rock</a>
                                </li>
                                                                            <li><a href="https://standingontherock.com/contact-us"><span></span> Contact Us</a></li>
                        <li><a href="https://standingontherock.com/about-us"><span></span> About Us</a></li>
                    </ul>
                </div>
            </div>

        </div>
        <div class="copy-right text-center">
            <p>Copyright © 2025. All rights reserved. designed & developed by excellis it.</p>
        </div>
    </div>
</footer>












    <script src="https://standingontherock.com/public/frontend_assets/js/jquery-3.7.1.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/slick-carousel/1.9.0/slick.min.js"></script>
    <script src="https://standingontherock.com/public/frontend_assets/js/bootstrap.bundle.min.js"></script>
    <script src="https://unpkg.com/aos@2.3.1/dist/aos.js"></script>
    <script src="https://standingontherock.com/public/frontend_assets/js/owl.carousel.min.js"></script>
    <script src="https://standingontherock.com/public/frontend_assets/js/custom.js?version=250711061154"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/toastr.js/latest/js/toastr.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/sweetalert2@11/dist/sweetalert2.min.js"></script>
    
    <script src="https://cdnjs.cloudflare.com/ajax/libs/jquery-validate/1.17.0/jquery.validate.min.js"></script>

    <script>
        
        
        
            </script>
            <script>
            $(document).ready(function() {
                $('#login-form').on('submit', function(e) {
                    e.preventDefault();

                    // Show loader
                    $('#login-btn').prop('disabled', true);
                    $('.btn-text').addClass('d-none');
                    $('.btn-loader').removeClass('d-none');

                    $.ajax({
                        url: "https://standingontherock.com/login-check",
                        type: "POST",
                        data: $(this).serialize(),
                        success: function(response) {
                            if (response.success) {
                                // Check if there's a redirect parameter
                                const redirect = $('input[name="redirect"]').val();
                                if (redirect === 'checkout') {
                                    window.location.href = "https://standingontherock.com/checkout";
                                } else {
                                    window.location.href = response.redirect_url;
                                }
                            } else {
                                toastr.error(response.message);
                                // Hide loader on error
                                $('#login-btn').prop('disabled', false);
                                $('.btn-text').removeClass('d-none');
                                $('.btn-loader').addClass('d-none');
                            }
                        },
                        error: function(xhr) {
                            const errors = xhr.responseJSON.errors;
                            $('.text-danger').text('');

                            if (errors) {
                                $.each(errors, function(key, value) {
                                    $('#' + key + '-error').text(value[0]);
                                });
                            }

                            // Hide loader on error
                            $('#login-btn').prop('disabled', false);
                            $('.btn-text').removeClass('d-none');
                            $('.btn-loader').addClass('d-none');
                        }
                    });
                });
            });
        </script>
        <script>
        $(document).ready(function() {
            $('#newsletter-form').on('submit', function(e) {
                e.preventDefault();
                var name = $(this).find('input[name="name"]').val();
                var email = $(this).find('input[name="email"]').val();

                // Validate inputs
                if (!name || !email) {
                    $('.newsletter-message').html(
                        '<span style="color: red;">Please fill in all fields.</span>');
                    return;
                }

                // Submit the form data via AJAX
                $.ajax({
                    url: 'https://standingontherock.com/newsletter/subscribe',
                    method: 'POST',
                    data: {
                        name: name,
                        email: email,
                        _token: 'JNakau4V7q0aBPrarqtdYD3wFFOTLtAf9SVfAOFU'
                    },
                    beforeSend: function() {
                        $('.newsletter-message').html(
                            '<span style="color: blue;">Subscribing...</span>');
                        $('#newsletter-form button').prop('disabled', true);
                    },
                    success: function(response) {
                        $('.newsletter-message').html(
                            '<span style="color: white;">Successfully subscribed!</span>');
                        $('#newsletter-form')[0].reset();
                    },
                    error: function(xhr) {
                        var message = xhr.responseJSON && xhr.responseJSON.message ? xhr
                            .responseJSON.message : 'Subscription failed. Please try again.';
                        $('.newsletter-message').html('<span style="color: red;">' + message +
                            '</span>');
                    },
                    complete: function() {
                        $('#newsletter-form button').prop('disabled', false);
                    }
                });
            });
        });
    </script>
</body>

</html>
