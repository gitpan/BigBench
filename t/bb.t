#!/usr/bin/perl -w

use Test::More;
use strict;
use File::Spec;

BEGIN
  {
  chdir 't' if -d 't';
  use lib 'lib';
  plan tests => 9;
  }

my ($options,$result);
my $zero = File::Spec->devnull();

while (<DATA>)
  {
  chomp();
  next if /^#/;			# skip comments
  $options = $_;
  $result = `../bb $options 2>$zero`;
  is (1,1,$options);
  }


1;

__DATA__
--def=empty.def --templ=test
--def=no_op.def --templ=test
--def=test.def --templ=test
--help
--version
--code='"ababba" =~ /a+/;'
--terse
--tight
--simulate=8
